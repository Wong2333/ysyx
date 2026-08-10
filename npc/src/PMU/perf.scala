package npc.PMU

import chisel3._

// 单个性能计数器黑盒：每实例一个 64 位计数器。
// ifndef SYNTHESIS：计数器不参与综合（仿真专用，不占流片面积）。
// 仿真结束时 C++ 通过 DPI-C export perf_read 按实例（svSetScope）读出打印。
class PerfCounterArray extends ExtModule {
  val io = IO(new Bundle {
    val clock = Input(Clock())
    val reset = Input(Bool())
    val event = Input(Bool())
  })
  addResource("/perf.v")
}

// 性能计数器声明。用法（在任意 Module 内）：
//   PerfCounter("icache_hit") := req_valid_s1 && icache.resp.hit
// 每个调用实例化一个计数器黑盒，实例名 = 事件名（即打印名，RTL 是名称的唯一来源；
// 生成的 Verilog 会被 scripts/gen_perf_names.py 解析，自动生成 C++ 侧的名字表）。
// 事件名需全局唯一；clock/reset 从模块作用域隐式获取。
object PerfCounter {
  private val usedNames = scala.collection.mutable.Set[String]()

  def apply(name: String): Bool = {
    require(!usedNames.contains(name), s"PerfCounter: duplicate name '$name'")
    usedNames += name
    val u = Module(new PerfCounterArray)
    u.suggestName(name)
    u.io.clock := Module.clock
    u.io.reset := Module.reset.asBool
    u.io.event
  }
}
