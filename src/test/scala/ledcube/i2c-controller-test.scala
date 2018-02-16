// See LICENSE for license details.

package ledcube

import ledcube._

import chisel3.iotesters
import chisel3.iotesters.{ChiselFlatSpec, Driver, PeekPokeTester}

class I2cControllerUnitTester(c: I2cController) extends PeekPokeTester(c) {
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

    poke(c.io.request.valid, false)
    poke(c.io.config.clock_threshold, 8)
    poke(c.io.config.clock_period, 16)

    step(1)

    poke(c.io.request.bits.address, 0x55)
    poke(c.io.request.bits.header, 0xAA)
    poke(c.io.request.bits.payload(0), 0x07)
    poke(c.io.request.bits.payload(1), 0x70)

    poke(c.io.request.bits.size, 4)
    poke(c.io.request.valid, true)

    step(1)

    poke(c.io.request.valid, false)

    step(2000)

}

class I2cControllerTester extends ChiselFlatSpec {
    private val backendNames = if(firrtl.FileUtils.isCommandAvailable("verilator")) {
        Array("firrtl", "verilator")
    }
    else {
        Array("firrtl")
    }
    for ( backendName <- backendNames ) {
            "I2C Controller" should s"work" in {
            Driver(() => new I2cController, backendName) {
                c => new I2cControllerUnitTester(c)
            } should be (true)
        }
    }
}
