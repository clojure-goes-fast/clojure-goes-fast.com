c2, level 4, benchmarks.IfNotLoop::testIf, version 3, compile id 1945

            0x0000000116cb21ec:   csel	w13, w3, w13, hi  // hi = pmore
            0x0000000116cb21f0:   add	w5, w13, w17
            0x0000000116cb21f4:   nop
            0x0000000116cb21f8:   nop
            0x0000000116cb21fc:   nop                                 ;*aload_3 {reexecute=0 rethrow=0 return_oop=0}
                                                                      ; - benchmarks.IfNotLoop::testIf@21 (line 75)
            0x0000000116cb2200:   add	x13, x2, w17, sxtw #3       ;*laload {reexecute=0 rethrow=0 return_oop=0}
                                                                      ; - benchmarks.IfNotLoop::testIf@24 (line 75)
            0x0000000116cb2204:   ldr	x14, [x13, #16]
            0x0000000116cb2208:   cmp	x14, #0xa
   0.03%    0x0000000116cb220c:   ldr	x15, [x13, #24]
            0x0000000116cb2210:   csel	x14, x4, x6, lt  // lt = tstop
            0x0000000116cb2214:   cmp	x15, #0xa
            0x0000000116cb2218:   ldr	x16, [x13, #32]
            0x0000000116cb221c:   csel	x15, x4, x6, lt  // lt = tstop
            0x0000000116cb2220:   cmp	x16, #0xa
   0.01%    0x0000000116cb2224:   ldr	x1, [x13, #40]
            0x0000000116cb2228:   csel	x16, x4, x6, lt  // lt = tstop
            0x0000000116cb222c:   add	x14, x0, x14
            0x0000000116cb2230:   cmp	x1, #0xa
            0x0000000116cb2234:   ldr	x0, [x13, #48]
            0x0000000116cb2238:   csel	x1, x4, x6, lt  // lt = tstop
            0x0000000116cb223c:   add	x14, x14, x15
            0x0000000116cb2240:   cmp	x0, #0xa
   0.02%    0x0000000116cb2244:   ldr	x15, [x13, #56]
            0x0000000116cb2248:   add	x14, x14, x16
            0x0000000116cb224c:   csel	x16, x4, x6, lt  // lt = tstop
            0x0000000116cb2250:   cmp	x15, #0xa
   6.63%    0x0000000116cb2254:   ldr	x7, [x13, #64]
            0x0000000116cb2258:   csel	x15, x4, x6, lt  // lt = tstop
            0x0000000116cb225c:   add	x14, x14, x1
            0x0000000116cb2260:   cmp	x7, #0xa
  12.87%    0x0000000116cb2264:   ldr	x13, [x13, #72]
            0x0000000116cb2268:   add	x14, x14, x16
            0x0000000116cb226c:   csel	x16, x4, x6, lt  // lt = tstop
            0x0000000116cb2270:   cmp	x13, #0xa
            0x0000000116cb2274:   add	x14, x14, x15
            0x0000000116cb2278:   csel	x13, x4, x6, lt  // lt = tstop
            0x0000000116cb227c:   add	x14, x14, x16
  79.61%    0x0000000116cb2280:   add	w17, w17, #0x8              ;*iinc {reexecute=0 rethrow=0 return_oop=0}
                                                                      ; - benchmarks.IfNotLoop::testIf@41 (line 75)
            0x0000000116cb2284:   add	x0, x14, x13                ;*ladd {reexecute=0 rethrow=0 return_oop=0}
                                                                      ; - benchmarks.IfNotLoop::testIf@39 (line 76)
            0x0000000116cb2288:   cmp	w17, w5
            0x0000000116cb228c:   b.lt	0x0000000116cb2200  // b.tstop;*if_icmpge {reexecute=0 rethrow=0 return_oop=0}
                                                                      ; - benchmarks.IfNotLoop::testIf@18 (line 75)
   0.21%    0x0000000116cb2290:   ldr	x13, [x28, #896]            ; ImmutableOopMap {r12=Oop c_rarg2=Oop }
                                                                      ;*goto {reexecute=1 rethrow=0 return_oop=0}
                                                                      ; - (reexecute) benchmarks.IfNotLoop::testIf@44 (line 75)
   0.03%    0x0000000116cb2294:   ldr	wzr, [x13]                  ;*goto {reexecute=0 rethrow=0 return_oop=0}
                                                                      ; - benchmarks.IfNotLoop::testIf@44 (line 75)
                                                                      ;   {poll}
            0x0000000116cb2298:   cmp	w17, w11
            0x0000000116cb229c:   b.lt	0x0000000116cb21d8  // b.tstop
            0x0000000116cb22a0:   cmp	w17, w10
            0x0000000116cb22a4:   b.ge	0x0000000116cb22c8  // b.tcont;*aload_3 {reexecute=0 rethrow=0 return_oop=0}
                                                                      ; - benchmarks.IfNotLoop::testIf@21 (line 75)
            0x0000000116cb22a8:   add	x11, x2, w17, sxtw #3
