// See LICENSE for license details.

package ledcube

import ledcube._

import chisel3.iotesters
import chisel3.iotesters.{ChiselFlatSpec, Driver, PeekPokeTester}

class RefreshControllerUnitTester(c: RefreshController) extends PeekPokeTester(c) {
//   private val gcd = c

//   for(i <- 1 to 40 by 3) {
//     for (j <- 1 to 40 by 7) {
//       poke(gcd.io.value1, i)
//       poke(gcd.io.value2, j)
//       poke(gcd.io.loadingValues, 1)
//       step(1)
//       poke(gcd.io.loadingValues, 0)

//       val (expected_gcd, steps) = computeGcd(i, j)

//       step(steps - 1) // -1 is because we step(1) already to toggle the enable
//       expect(gcd.io.outputGCD, expected_gcd)
//       expect(gcd.io.outputValid, 1)
//     }
//   }

    poke(c.io.i2c0.scl_fb, false)
    poke(c.io.i2c1.scl_fb, false)
    poke(c.io.i2c2.scl_fb, false)
    poke(c.io.i2c3.scl_fb, false)

    step(100000)

}

class RefreshControllerTester extends ChiselFlatSpec {
    private val backendNames = if(firrtl.FileUtils.isCommandAvailable("verilator")) {
        Array("firrtl", "verilator")
    }
    else {
        Array("firrtl")
    }
    for ( backendName <- backendNames ) {
            "Refresh Controller" should s"work" in {
            Driver(() => new RefreshController, backendName) {
                c => new RefreshControllerUnitTester(c)
            } should be (true)
        }
    }
}
