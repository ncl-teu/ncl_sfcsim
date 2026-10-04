# 重跑前正确性审计（2026-10-04）

## 结论

**本文件前半部分保留修复前的诊断记录；本次已落实修复，见末尾“修复落地与预检”。**
修复前不建议立即重跑的结论不再代表当前状态。当前小规模预检通过，仍不能将它
视为所有 seed、所有算法或物理网络模型的无缺陷认证。

除了已经修复的执行重叠和同核心 sibling 队列读取问题，本次发现了会影响
DHEFT 镜像就绪估计的缺陷，以及 NHEFT 的传输边界缺陷。还需要明确执行
空隙搜索、虚拟节点资源统计和路径带宽模型的口径。

本次只新增诊断程序和审计记录，没有继续修改生产算法、实验参数、历史
原始数据、图表或论文。以下“已复现”不等于“已经修复”。

## 审计范围和证据

- 源码：当前入口 `NFVSchedulingTest`、共享执行排程、DHEFT、NHEFT、IRT-only
  G-NHEFT、VM 镜像状态、动态带宽槽，以及 six 的生成和统计脚本。
- 对现有 9,574 项执行就绪/核心利用率回归断言重新验证，全部通过。
- 额外验证门控条件真值表：384 个组合全部符合当前 EFT + 可选 AND/OR gate 规则。
- six 的 e01x--e08z 共 24 份正式场景配置，每份临时复制并设 seed=741896，
  对三个算法独立深拷贝运行，共 72 份调度。每份工作流包含 444 个节点，含虚拟节点。
- 检查完整排程、执行时长、依赖数据/镜像就绪、同 vCPU 重叠、实际核心并发负载，
  均通过。NHEFT/G-NHEFT 的已提交路径占用均未超对应路径容量，真实传输的
  预留字节量与镜像大小一致（浮点误差约 1e-11）；本次最大单路径预留条目数为 38。
- 只读检查 six 每场景最新的 500-seed CSV，共 12,000 行：没有非 OK 记录、
  重复 seed、非有限/非正的关键读数、CCR+IDR 与 NCCR 的异常差异，或门控记录异常。
  **这些 CSV 的 OK 状态并没有验证完整排程可行性，也不是本次代码修正后的重跑。**
- 主机总出口和虚拟资源口径额外在 e01x/e08z 等小规模诊断中检查。

全部审计配置和日志在 `/tmp/nheft-prererun.sHJ5Pl/`，未向正式 run 目录写入数据。
小例子和审计代码保存在 `regression/net/gripps/cloud/nfv/regression/PreRerunAudit.java`。
这不是全仓库、全部算法、全部 seed 的正确性认证。

## 一、已复现的镜像状态问题：优先处理

### 1. DHEFT 的本地缓存复用仍等待无关下载队列

位置：`DHEFT_VNFAlgorithm.buildVmSourcePlan()` / `findBestDownloadPlan()`。

程序先计算 `max(imageReady, sourceQueueTail, targetQueueTail)`，随后才判断
“同 VM/同物理主机，无需传输”。所以虽然传输时长为零，IRT 仍被无关下载队列推迟。
本地缓存的 registry 只在 ready <= EPS 时直接命中；已经承诺在正时刻完成的镜像
仍进入上述逻辑，而离线排程中的缓存就绪时间通常正是正数。

复现：目标 VM 的镜像于 10 就绪，目标主机另一个镜像下载到 100。
任务复用本地镜像，DHEFT 返回 IRT=100，而不是 10。

在 24 份真实配置的抽查中均出现：每份 114--208 次目标 VM 已有就绪记录却返回
更晚 IRT 的情况。e01x/741896 为 114 次，最大额外镜像等待 6.949；
e08z/741896 为 207 次，最大额外等待 166.401。

这会改变候选 EFT，有可能拖慢 DHEFT、影响资源选择，从而夸大相对基准的收益。
不代表每次 IRT 变化都改变最终 makespan，但不能假设其对结果无影响。
应让无网络传输的本地复用等待镜像真正就绪，而非无关网络队列；
同主机本地访问分支也需要统一处理。

### 2. DHEFT 的零大小镜像仍等待下载队列

位置：`DHEFT_VNFAlgorithm.findBestDownloadPlan()` / `buildRepoPlan()`。

NHEFT 明确跳过 `imageSize <= 0`；DHEFT 没有对应入口保护，时长虽然为零，
IRT 仍可等于非零下载队列尾。小例子得到 expected IRT=0 / actual IRT=100。
24 份工作流抽查均发现 1 个零镜像节点有非零 IRT。

这在虚拟 END 节点上尤其不应被当成镜像获取需求。它不一定推迟每个样本的最终完成
时间，但模型和基准之间的这项差异必须消除或明确说明。

### 3. 镜像 registry 会把已就绪时间覆盖为更晚时间

位置：`VM.registerImageReadyTime()` 和共享 `markImageTypeForVM()`。

当前直接覆盖，复现为先记 10，再记 100，结果变成 100。
在无驱逐/失效模型下，镜像一旦在 10 可用，后续引用不应该让它重新变为 100 才可用。
问题 1 可以触发这种后移，继续影响后续复用。

建议登记同一镜像的最早有效就绪时间，不能只是修复首次查询而留下状态后移。
这种修改需核对所有调用者以及预加载镜像语义，不能无检查地全局替换。

## 二、执行可行性已改善，但“最早可行”搜索仍不完整

位置：共享 `BaseVNFSchedulingAlgorithm.calcEST()`。

### 4. 多任务队列漏查第一个任务之前的空隙

当队列有两项以上时，只查相邻任务间空隙和尾部，不查首任务之前。
复现：已有 `[20,30)`、`[40,50)`；候选 DRT=IRT=0、计算时长 5、负载可行。
真正可行的最早开始为 0，程序返回 30。

### 5. 单任务队列尾部可能无条件等所有 sibling 结束

单任务无法前插时，约束模式直接取同核心各 vCPU 最大 CT，而不是先验证候选尾部区间。
复现：候选 CPU `[0,10)`，sibling `[0,100)`，负载允许并行。
`isAssignedInDuration(10,15)` 返回 true，但 calcEST 返回 100。

这两项都不会产生新的重叠，却会使返回值不是“最早可行开始”，影响候选 EFT 和 gate。
它们是原有搜索策略的局限，不是上一轮修复新引入的。若保留保守搜索，论文不能将其
描述为完整的最早插入搜索；若目标是标准 HEFT 式插入，则应统一修正并测试。
mode=0 多任务尾排策略也同样不是完整 HEFT 插入，本次没有擅自改变它。

另外，若以后将核心线程数减到 1、或降低负载上限，候选自身就可能不可行。
现有尾部 fallback 不保证拒绝这种情况；当前两线程/上限 75/任务 usage<=80 的
配置不会因单个任务自身触发这一风险，不能把此结论推广到任意参数。

## 三、动态带宽存在已复现的边界缺陷

### 6. 4096 步耗尽后，镜像未传完仍返回完成时间

位置：`NHEFT_VNFAlgorithm.simulateDynamicDownload()`；`DynamicResult` 也使用固定数组。

循环退出时没有检查剩余字节是否为零，就写入 `finishTime`。
复现：1,000,000 单位镜像，多事件槽只传输了 179149.69289999086，
仍报告 finish=2047.500001，且 segmentCount=4096。

这是确定的完成性缺陷。当前 24 个审计种子没触发，不代表所有 seed 和今后规模都安全。
需要安全扩容/正确遍历事件，或至少在未完成时明确失败，不能静默产生正常结果。

### 7. 极近事件边界越界，预留失败又被忽略

位置：`simulateDynamicDownload()` 的 `Math.max(EPS, nextChange-t)` 和
`commitDynamicReservation()` 的未检查 `reserveBW()` 返回值。

下一占用事件在 1e-7，程序仍生成跨度 1e-6 的当前带宽段，越过变化边界。
该段不能预留；commit 忽略 false，后续却仍使用计划完成时间。
复现：计划传输 100，实际预留仅覆盖 99.99990000000001，没有报错。

这个例子丢失的量很小，但它证明“预计传完”和“提交成功”并非总是一致。
建议精确推进半开区间事件，检查每个必要槽的预留，并避免仅一侧成功的部分提交。
本次 48 份 NHEFT/G-NHEFT 工作流的字节检查未发现这种失配。

### 8. 区间最小剩余带宽查询错误累加非并发占用

位置：`BandwidthTimeSlot.getAvailableBW(start,end)`。

容量 100，`[0,1)` 占用 30，`[1,2)` 占用 30。
整个 `[0,2)` 的最小剩余应为 70，却返回 40；两个非并发占用被累加。

当前 NHEFT 通常按事件拆段，所以普通原子段不一定触发此缺陷；
NPHEFT 等跨事件整段查询会受影响。它仍是当前 helper 的实际逻辑错误。
应按并发峰值求区间最小剩余，而非把所有相交记录一起求和。

## 四、不是简单代码 bug，但重跑前应确认研究口径

### 9. 路径容量不等于仓库/主机总出口容量

NHEFT 的占用键是 Host-pair 和 DC-pair，不同主机对之间没有聚合端点约束。
现有论文已明确使用 pair-slot 模型，因此不能直接把它宣判为违反该模型的代码错误。
但它不能同时被解释为共享物理网卡总带宽模型。

实际复现（NHEFT 和 G-NHEFT 均如此）：

| 配置 / seed | 仓库配置 BW | 多路径仓库出口合计峰值 |
| --- | ---: | ---: |
| e01x / 741896 | 2000 | 5786 |
| e08z / 741896 | 2000 | 8605 |

如果 2000 表示仓库总出口上限，就需要共享 repository/host/DC 端点占用；
如果它表示各逻辑路径容量的参数，应保留并明确其抽象性，不可声称满足总出口约束。
这会影响研究模型和结果，不能当作一行普通 bug 修复擅自改变。

DHEFT 的跨 DC 缓存传输只取源/目标 host 带宽，NHEFT 同时取 DC 路径容量。
这也是应清楚区分的带宽模型差异，不能说两者在所有静态路径容量细节上完全相同。

### 10. 虚拟 START/END 参与 used 集合和资源计数

`SFCGenerator` 创建零工作量、零 usage 的虚拟节点，调度器仍将其 vCPU 加入
`assignedVCPUMap`。首次实际任务之前 used 集合已非空，会成为 gate 的参照。

e01x/741896 的复现：

| 算法 | 输出的 used vCPU 数 | 有正工作量任务的 vCPU 数 |
| --- | ---: | ---: |
| DHEFT | 56 | 55 |
| NHEFT | 59 | 58 |
| G-NHEFT | 41 | 41 |

因此，输出计数不是严格的“执行实际计算任务的资源数量”；不同算法的虚拟-only
资源数量可能不同。虚拟节点又能影响首次 gate，不只是事后报表扣掉一项就解决。
这不是同 vCPU 重叠问题；是否保留逻辑节点 assignment 作为 activation 的定义，
需要明确决定。若目标是实际激活计算资源，则应区分 DAG bookkeeping 和资源开启。

## 五、实验运行链路的防护不足

以下问题应作为重跑前检查，不代表现有 12,000 行已经因此损坏：

- runner 运行的是 `classes/`，不会自动编译。源码修改后必须确认实际加载的 class
  已更新；实验中途重新编译可能让不同 seed 使用不同版本。
- 当前 `status=ok` 只表示退出码正常且关键输出存在，没有验证排程不变量、
  有限正值、镜像字节完成或完整预留。应在 Java 输出正式指标前加入失败即停止的校验。
- manifest 记录参数/seed，但没有冻结 class/source 版本；“最新 500-seed run”本身
  不是版本保证。建议同时记录 commit、dirty diff、class 哈希、JDK、完整实际配置。
- analysis 通过目录修改时间选 run，且不要求 completed；可能读取仍在运行的部分
  结果，或混合不同代码版本。正式论文处理应显式固定 24 份 run 的来源并核对完整性。
- 新建共享 seeds 文件时使用固定 `.tmp` 名；首次同时启动多场景存在文件竞争。
  当前 six 已有合法 seeds_500.json，正常读取该文件不触发这一路径。
- 入口仍无条件运行 `RandomVNFClusteringAlgorithm`。因此 six 虽只统计三个方法，
  Java 实际还跑一个额外背景算法。它使用独立深拷贝，未发现直接污染 D/N/G 调度，
  但浪费算力；整次 Java 的 `time_sec` 也不能当作单个 NHEFT 的调度耗时。

## 建议顺序

1. 先修正 DHEFT 本地/零镜像处理与镜像 registry，保证对照基准没有不必要等待。
2. 明确“最早插入”、虚拟节点 activation 和 pair-slot/端点总容量的定义，再决定
   哪些需要代码调整、哪些保留为有清晰边界的模型。
3. 补齐动态传输完成性、事件边界、预留成功/一致性检查及针对性回归。
4. 给正式运行增加不变量校验和版本记录；错误必须暴露，不能继续产出普通 OK 行。
5. 用所有 24 份配置各 1--3 个固定种子做预检，再开始完整 500-seed 研究。

这些处理是否使结果“更好看”不是修复标准。优先保证模型一致、排程可行、
数据可以追溯；不能为了保留已有论文读数而保留缺陷。

## 复现命令

普通已有回归（需 JDK 与 classes 匹配）：

```sh
ant regression_tests
```

诊断小例子与一个完整工作流：

```sh
java -Xmx1000m -cp 'test/regression:classes:lib/*' \
  net.gripps.cloud.nfv.regression.PreRerunAudit small \
  experiments/six/e01x/b01x.properties

java -Xmx1000m -cp 'test/regression:classes:lib/*' \
  net.gripps.cloud.nfv.regression.PreRerunAudit workflow \
  /path/to/temporary-scenario.properties
```

本次使用 Java 8，并将 Ant build/tests 定向到
`/tmp/core-usage-java8.1K8d6o/`，没有替换正式 `classes/`。
PreRerunAudit 是诊断程序：打印已复现的缺陷数值并保留现状，退出码 0
**不表示列出的缺陷已经修复**。执行可行性断言失败仍会抛错。

## 修复落地与预检（2026-10-04）

本节更新当前状态；前面的读数与位置说明是修复前证据，不用于解释新结果。

### 已修复

- DHEFT 的同 VM/同主机复用只等镜像实际就绪；不再等无关 Host 下载队列。
- DHEFT/NHEFT 对零镜像或关闭下载模型的任务返回零传输时间；零镜像不写缓存。
- VM registry 保留镜像最早就绪时间，也保留预加载镜像在时刻 0 可用的语义。
- `calcEST` 统一搜索 front/internal/tail 空隙，包括 mode=0 的多任务队列；
  核心约束仍采用原先的每个 sibling 区间最大 usage 汇总/平均/四舍五入规则。
  在核心负载已可行时不再无条件等待所有 sibling 结束；没有可行候选时明确失败。
- 仅 type 为虚拟 START/END 且 workload/image/usage 都为零的 DAG 边界节点，
  只保存依赖位置与时间、推进 free list；不进入执行队列、缓存、used map 或 host set。
- 动态传输沿精确半开区间事件推进，不再使用 EPS 跳过带宽边界；移除 4096 步/段截断。
  所有字节必须传完，分段数组按需扩容；无法推进或无法表示的时间明确报错。
- 提交前检查整个动态计划；提交时检查两个路径槽的预留返回值，失败回滚本次新增记录。
  提交的积分字节量必须与镜像大小一致，容许浮点数累计误差。
- `BandwidthTimeSlot.getAvailableBW` 用实际并发峰值计算最小剩余带宽；
  不再累计时间上相邻但不并发的预留。不可满足的带宽需求不返回假可行时间。
- 新增 `SchedulingValidator`，在 DHEFT/NHEFT/mode2 输出正式指标前检查：
  完整调度、唯一/正确队列、执行时长与重叠、IRT/DRT 就绪、核心并发上限、资源计数与 makespan。
  NHEFT 另外验证路径占用上限；异常导致非零退出，不发布普通 OK 结果。

### 保持不变与模型边界

- rank/任务排序、tie 策略、tolerance 数值及逻辑、Comp/DRT/IRT gate 的 ALL/ANY 规则不变。
  384 种 gate 真值组合另有回归断言。
- 场景参数、500 个候选 seeds、桶区间、10%/20% 构成筛选规则与统计/绘图定义不变。
- 继续使用 Host-pair/DC-pair 逻辑路径模型，**没有新增端点总出口容量约束**。
  仓库跨多个路径的合计峰值可以高于各路径容量参数，不能解释为物理总网卡上限。
- DHEFT 与 NHEFT 跨 DC 缓存传输的容量处理差异仍是原有模型差异，未擅自统一。
- 虚拟节点 activation 和完整插入搜索是明确修正，可能改变资源数、gate 的初始参照和 makespan；
  不能只从旧 CSV 扣除一项资源，也不能混用旧/新算法结果。
- 未修改历史 CSV、日志、图表、论文或 five 的历史运行/统计脚本；未提交 Git。

### six 的运行与正式统计保护

- `common_runner.py` 先拒绝明显落后的编译结果，再冻结本次的 class/JAR；
  记录代码版本标记、class/source/library/runner SHA-256、Git HEAD/diff、JDK 与完整实际参数快照。
  运行中修改源码或重新编译不会改变已经冻结的批次。
- Java 新增 `run_random_clustering` 开关：缺失时默认开启、保留旧入口习惯；six 显式关闭，
  所以每个 seed 只运行 DHEFT、NHEFT、IRT-only GHEFT。`time_sec` 仍是整次 Java 的时间，
  包含生成输入、三个调度器、深拷贝及校验，不能作为单个算法的计算耗时。
- 三个算法指标必须有限/有效，并包含正确版本与校验通过标记；任何失败保存当次日志/CSV后停止场景。
  批次只有完整 500 次 OK 后才标记 `complete=true`；中断退出码为 130。
- seed 文件使用独立临时文件与不可覆盖的原子发布，消除首次并发写入同一 `.tmp` 的竞争。
  run 目录加入微秒，拒绝覆盖同名目录；CSV 字段及输出算法标签保持原样。
- `analyze_results.py` 默认选目标 seed 数量下最新的**完成且有版本记录**的批次，不再用目录 mtime；
  核对实际 CSV 行数、唯一/相同 seed 顺序、版本/依赖一致与成对有限数据。
  保存 `analysis_provenance.json` 固定实际采用的 24 个来源；版本混用或不完整数据拒绝统计。
- 查看历史已完成数据需显式 `--allow-legacy-runs`；该选项不修复旧数据，也不允许混合不同版本。
  `check_valid_counts.py` 仍可只读查看正在生成的结果。

### 验证

- `ant regression_tests`：执行空隙 6,416、核心 usage 4,134、状态/带宽/gate 44,402 项断言，
  合计 54,952 项；同时用 Java 8 独立输出目录构建与验证。
- Python 回归：并发 seed 发布、有限指标/校验标记、运行版本冻结、最新完整批次选择、
  混合版本拒绝；所有测试只用临时文件，不改正式数据。
- 全 24 份 six 场景配置，各抽查 seeds 741896/430515/888075，共 72 份工作流、216 次调度。
  没有执行重叠、IRT/DRT 违规、核心超载或路径超载；字节失配为零，
  DHEFT 本地/零镜像额外等待为零，实际任务前的 used 集合为空。
- 真正经过 common runner 的 e01x 两个 seed、e08z 一个 seed 的冒烟只写入系统临时目录；
  验证配置覆盖、Java 校验、CSV/manifest、冻结版本能衔接；限量批次不标为完成。
- 预检结果位于 `/tmp/nheft-fix-20261004/preflight/`，不是正式研究数据；未运行完整 24×500。

复查命令：

```sh
ant regression_tests
experiments/.venv/bin/python regression/six_runner_regression.py
```

正式启动仍用原场景入口，例如：

```sh
experiments/.venv/bin/python experiments/six/e01x/run_experiment.py
```

## Additional boundary fixes (2026-10-04)

This round fixes only the three issues reproduced by the subsequent Java audit.
It does not revise the scheduling policy, network model, experiment parameters,
historical results or paper. Changes from the earlier execution/core/state
repairs remain in place.

- NHEFT returns an infeasible sentinel when no image plan exists, rather than
  IRT=0. Its duration compatibility method no longer falls back to a static
  repository estimate. An unavailable commit plan throws before execution
  queues, cache metadata, activation maps or download counters are updated.
  Actual cache reuse, zero images and disabled download modeling still work.
- NFVEnvironment explicitly requires DCs 0 and 1 before constructing its
  repository. The repository stays in DC 1 with its original identity and
  bandwidth; a one-DC setup is rejected, not silently relocated or repaired.
- CloudUtil's uniform floating-point generators use `max-min`, not the integer
  sampling width `max-min+1`. Invalid/nonfinite ranges are rejected; rounded
  samples are clamped to their configured bounds. `genDouble` still consumes
  one RNG draw for a fixed range, while `genDouble2` retains its existing
  no-draw fixed-range path. Integer sampling and Gaussian sampling are unchanged
  for valid inputs.

### Verification of this round

- `BoundaryRegressionTest` fails against frozen pre-fix production classes
  with `repository DC missing: did not fail` and passes after the fix.
- Existing 54,952 assertions and the 30,142 new boundary assertions pass
  (85,094 total), including an independent Java 8 build.
- Every one of the 24 formal six profiles was copied to a temporary file with
  seed 664527. Serialized SHA-256 hashes of the complete generated SFC and
  environment match before/after, as does the next uniform RNG draw.
- e01x, e04y and e08z were each scheduled by DHEFT, NHEFT and IRT-only
  G-NHEFT before/after. All nine diagnostic outputs match exactly, including
  makespan, used vCPUs, overlap/core/link checks and transfer byte errors.
- An e01x/664527 smoke run through the actual NFVSchedulingTest entry point,
  with the usual launcher classpath and only DHEFT/NHEFT/GHEFT enabled, also
  produces identical complete stdout before/after. All three validation markers
  pass. The five Python runner/provenance regression tests pass as well.
- Evidence and frozen builds are in `/tmp/nheft-boundary-fix-20261004/`.
  These are spot checks, not a new 24-by-500 study or proof of the entire
  simulator. No formal experiment results were overwritten.

Normal six profiles use DCs 0 through 5, positive path/repository capacities,
and a fixed core speed multiplier of 1.0. The reproduced exceptional conditions
are outside these settings. The comparisons above concern this boundary-only
round, not the earlier fixes that can change historical metrics.

Runtime provenance remains enforced: compiled class hashes change after a
repair, so do not mix different frozen build signatures in one formal study.

## Numerical transfer completion repair (2026-10-04)

The subsequent formal batch exposed a numerical boundary missed by the earlier
preflight. Eleven scenario/seed logs fail with `Unrepresentable image transfer
segment` in NHEFT's dynamic simulation (also used by G-NHEFT). This is not a
Python concurrency failure. The earlier preflight was insufficient to certify
these seeds; its passing results must not be read as proof of a complete study.

### Reproduction and repair

The frozen failing e04z/610245 build was replayed with temporary diagnostic
instrumentation. A 456-unit image had recorded 455.99999999999983 units; the
remaining 1.7053025658242404e-13 units at bandwidth 621 could not advance the
double-precision timestamp 13.955682792730618. The resulting zero-duration tail
triggered the strict segment guard even though the actual transfer already
satisfied the existing byte-conservation check.

- Both final-transfer branches now use the same helper. A zero-duration final
  tail is omitted only if positive-duration segments already exist and their
  transferred bytes satisfy the unchanged tolerance `max(1e-7, imageSize*1e-9)`.
- Positive-duration transfers are not skipped. Genuine insufficient progress,
  invalid segments, incomplete bytes and failed reservations still fail.
- Bandwidth capacity checks, atomic reservation/rollback, ranking, EFT tolerance,
  gate rules, scenario parameters and the pair-link network model are unchanged.
- No formal CSV, log, plot or paper was edited. No failed seed was silently
  skipped or rewritten as OK.

### Verification

- The new minimal regression fails against the old frozen production classes
  and passes after the repair. It covers both final-transfer branches, 2,000
  randomized numerical traces, and a genuinely unrepresentable nonzero transfer
  that must still fail.
- All five Java suites pass: 96,108 assertions, in fresh Java 21 and Java 8
  builds. The workspace `classes/` was rebuilt and passes the same suites.
  All five Python runtime/provenance tests also pass.
- All 11 observed failing scenario/seeds pass the independent workflow audit
  and the actual `NFVSchedulingTest` entry point, each running DHEFT, NHEFT and
  IRT-only GHEFT. The independent checks find no execution overlap, core/link
  overload or transfer-byte mismatch. All formal validation markers pass.
- Failing cases replayed: e01x/336806, e01y/472131, e02x/753523,
  e02y/635612, e02z/460446, e03x/786261, e03y/69527, e03z/282392,
  e04x/455331, e04y/79088 and e04z/610245.
- Every formal six scenario also passes the actual Python runner with seeds
  741896 and 430515: 48 Java invocations / 144 algorithm runs. Their frozen
  class/library signatures agree, configuration overrides are checked, and
  capped batches correctly remain `complete=false` rather than claiming 500
  completed seeds. All these outputs are in a temporary directory.
- Evidence: `/tmp/nheft-roundoff-20261004/`; runner evidence:
  `/private/var/folders/sn/m3zm7rxx6b5gxvh65g27mlw40000gn/T/nheft-launch-preflight-83a6fleq/`.
  These checks are not a full 24-by-500 rerun or a proof of every simulator path.

### Restart requirement

Already-started batches retain their frozen old classes. Rebuilding the workspace
does not patch those batches. Stop old jobs and launch fresh run directories
using the repaired build for all 24 formal scenarios; preserve the old partial
results as diagnostic evidence. Do not append repaired rows to an old CSV or
combine old/new class signatures in one study. The analysis version safeguards
remain enabled.
