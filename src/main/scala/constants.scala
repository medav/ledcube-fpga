package ledcube.constants

import chisel3._
import chisel3.util._

object TlcConstants {
    val ALLCALLADDR = "h68".U(8.W)

    val AUTOINC_NONE = "h00".U(8.W)
    val AUTOINC_ALL = "h80".U(8.W)
    val AUTOINC_BRIGHTNESS = "hA0".U(8.W)
    val AUTOINC_CONTROL = "hC0".U(8.W)

    val READ = true.B
    val WRITE = false.B
}

object CommConstants {

    //
    // N.B. These are purposefully chosen to be "complex" bit patterns so
    // there's less change of a random garbage byte coming in through the UART
    // that could match.
    //

    val OP_CSRW = "hA0".U(8.W)
    val OP_READFRAME = "hA1".U(8.W)
}

object TlcRegisters {
    val MODE1 = 0.U(8.W)
    val MODE2 = 1.U(8.W)
    val PWM0 = 2.U(8.W)
    val PWM1 = 3.U(8.W)
    val PWM2 = 4.U(8.W)
    val PWM3 = 5.U(8.W)
    val PWM4 = 6.U(8.W)
    val PWM5 = 7.U(8.W)
    val PWM6 = 8.U(8.W)
    val PWM7 = 9.U(8.W)
    val PWM8 = 10.U(8.W)
    val PWM9 = 11.U(8.W)
    val PWM10 = 12.U(8.W)
    val PWM11 = 13.U(8.W)
    val PWM12 = 14.U(8.W)
    val PWM13 = 15.U(8.W)
    val PWM14 = 16.U(8.W)
    val PWM15 = 17.U(8.W)
    val GRPPWM = 18.U(8.W)
    val GRPFREQ = 19.U(8.W)
    val LEDOUT0 = 20.U(8.W)
    val LEDOUT1 = 21.U(8.W)
    val LEDOUT2 = 22.U(8.W)
    val LEDOUT3 = 23.U(8.W)
    val SUBADR1 = 24.U(8.W)
    val SUBADR2 = 25.U(8.W)
    val SUBADR3 = 26.U(8.W)
    val ALLCALLADR = 27.U(8.W)
    val IREF = 28.U(8.W)
    val EFLAG1 = 29.U(8.W)
    val EFLAG2 = 30.U(8.W)
}