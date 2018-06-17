module LedcubeTop(
    input clock,
    input reset,
    inout i2c0_sda,
    inout i2c0_scl,
    output i2c0_resetn,
    inout i2c1_sda,
    inout i2c1_scl,
    output i2c1_resetn,
    inout i2c2_sda,
    inout i2c2_scl,
    output i2c2_resetn,
    inout i2c3_sda,
    inout i2c3_scl,
    output i2c3_resetn,
    output [7:0] layer_active,
    input uart_rx,
    output uart_tx
);

    wire  io_i2c0_sda_out;
    wire  io_i2c0_sda_in;
    wire  io_i2c0_scl_out;
    wire  io_i2c0_scl_in;
    wire  io_i2c0_resetn;
    wire  io_i2c1_sda_out;
    wire  io_i2c1_sda_in;
    wire  io_i2c1_scl_out;
    wire  io_i2c1_scl_in;
    wire  io_i2c1_resetn;
    wire  io_i2c2_sda_out;
    wire  io_i2c2_sda_in;
    wire  io_i2c2_scl_out;
    wire  io_i2c2_scl_in;
    wire  io_i2c2_resetn;
    wire  io_i2c3_sda_out;
    wire  io_i2c3_sda_in;
    wire  io_i2c3_scl_out;
    wire  io_i2c3_scl_in;
    wire  io_i2c3_resetn;

    wire [0:0] bram_write_enable;
    wire [8:0] bram_write_addr;
    wire [7:0] bram_write_data;
    wire [8:0] bram_read_addr;
    wire [7:0] bram_read_data;

    assign i2c0_sda = io_i2c0_sda_out ? 1'bZ : 1'b0;
    assign io_i2c0_sda_in = i2c0_sda;
    assign i2c0_scl = io_i2c0_scl_out ? 1'bZ : 1'b0;
    assign io_i2c0_scl_in = i2c0_scl;
    assign i2c0_resetn = io_i2c0_resetn;

    assign i2c1_sda = io_i2c1_sda_out ? 1'bZ : 1'b0;
    assign io_i2c1_sda_in = i2c1_sda;
    assign i2c1_scl = io_i2c1_scl_out ? 1'bZ : 1'b0;
    assign io_i2c1_scl_in = i2c1_scl;
    assign i2c1_resetn = io_i2c1_resetn;

    assign i2c2_sda = io_i2c2_sda_out ? 1'bZ : 1'b0;
    assign io_i2c2_sda_in = i2c2_sda;
    assign i2c2_scl = io_i2c2_scl_out ? 1'bZ : 1'b0;
    assign io_i2c2_scl_in = i2c2_scl;
    assign i2c2_resetn = io_i2c2_resetn;

    assign i2c3_sda = io_i2c3_sda_out ? 1'bZ : 1'b0;
    assign io_i2c3_sda_in = i2c3_sda;
    assign i2c3_scl = io_i2c3_scl_out ? 1'bZ : 1'b0;
    assign io_i2c3_scl_in = i2c3_scl;
    assign i2c3_resetn = io_i2c3_resetn;

    block_mem bram(
        clock,
        bram_write_enable,
        bram_write_addr,
        bram_write_data,
        clock,
        bram_read_addr,
        bram_read_data
    );

    LedCubeController lcc(
        clock,
        reset,
        io_i2c0_sda_out,
        io_i2c0_sda_in,
        io_i2c0_scl_out,
        io_i2c0_scl_in,
        io_i2c0_resetn,
        io_i2c1_sda_out,
        io_i2c1_sda_in,
        io_i2c1_scl_out,
        io_i2c1_scl_in,
        io_i2c1_resetn,
        io_i2c2_sda_out,
        io_i2c2_sda_in,
        io_i2c2_scl_out,
        io_i2c2_scl_in,
        io_i2c2_resetn,
        io_i2c3_sda_out,
        io_i2c3_sda_in,
        io_i2c3_scl_out,
        io_i2c3_scl_in,
        io_i2c3_resetn,
        layer_active,
        uart_tx,
        uart_rx,
        bram_read_addr,
        bram_read_data,
        bram_write_addr,
        bram_write_enable,
        bram_write_data
    );
    
endmodule
