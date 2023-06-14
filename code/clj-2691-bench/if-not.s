c2, level 4, benchmarks.IfNotLoop::testIfNot, version 3, compile id 2026
                                                                      ;   {metadata(&apos;clojure/core$not&apos;)}
movk	x20, #0xd060
sub	w11, w17, w16
cmp	w2, w16
sub	w10, w11, #0x1
csel	w10, wzr, w10, lt  // lt = tstop
cmp	w10, #0x7d0
csel	w10, w7, w10, hi  // hi = pmore
add	w19, w10, w16               ;*aload_3 {reexecute=0 rethrow=0 return_oop=0}
                                    ; - benchmarks.IfNotLoop::testIfNot@21 (line 85)
ldr	w11, [x25, #20]             ;*getfield testIfNot {reexecute=0 rethrow=0 return_oop=0}
                                    ; - benchmarks.IfNotLoop::testIfNot@29 (line 86)
add	x26, x0, w16, sxtw #3       ;*laload {reexecute=0 rethrow=0 return_oop=0}
                                    ; - benchmarks.IfNotLoop::testIfNot@24 (line 85)
ldar	w21, [x13]                  ;*getfield root {reexecute=0 rethrow=0 return_oop=0}
                                    ; - clojure.lang.Var::getRawRoot@1 (line 260)
                                    ; - clj_code$test_if_not::invokeStatic@3 (line 11)
                                    ; - clj_code$test_if_not::invokePrim@1
                                    ; - benchmarks.IfNotLoop::testIfNot@34 (line 86)
lsl	x23, x11, #3                ;*getfield testIfNot {reexecute=0 rethrow=0 return_oop=0}
                                    ; - benchmarks.IfNotLoop::testIfNot@29 (line 86)
ldr	x24, [x26, #16]             ;*laload {reexecute=0 rethrow=0 return_oop=0}
                                    ; - benchmarks.IfNotLoop::testIfNot@24 (line 85)
ldr	w10, [x23, #8]              ; implicit exception: dispatches to 0x000000011430bcb0
cmp	w10, w6
b.ne	0x000000011430bc40  // b.any
ldar	w10, [x22]                  ;*getfield root {reexecute=0 rethrow=0 return_oop=0}
                                    ; - clojure.lang.Var::getRawRoot@1 (line 260)
                                    ; - clj_code$test_if_not::invokeStatic@3 (line 11)
                                    ; - clj_code$test_if_not::invokePrim@1
                                    ; - benchmarks.IfNotLoop::testIfNot@34 (line 86)
ldr	w3, [x25, #20]              ;*getfield testIfNot {reexecute=0 rethrow=0 return_oop=0}
                                    ; - benchmarks.IfNotLoop::testIfNot@29 (line 86)
lsl	x21, x21, #3                ;*getfield root {reexecute=0 rethrow=0 return_oop=0}
                                    ; - clojure.lang.Var::getRawRoot@1 (line 260)
                                    ; - clj_code$test_if_not::invokeStatic@3 (line 11)
                                    ; - clj_code$test_if_not::invokePrim@1
                                    ; - benchmarks.IfNotLoop::testIfNot@34 (line 86)
ldr	w11, [x21, #8]              ; implicit exception: dispatches to 0x000000011430bc1c
cmp	w11, w20
lsl	x23, x3, #3                 ;*getfield testIfNot {reexecute=0 rethrow=0 return_oop=0}
                                    ; - benchmarks.IfNotLoop::testIfNot@29 (line 86)
b.ne	0x000000011430bc8c  // b.any;*checkcast {reexecute=0 rethrow=0 return_oop=0}
                                    ; - clj_code$test_if_not::invokeStatic@6 (line 11)
                                    ; - clj_code$test_if_not::invokePrim@1
                                    ; - benchmarks.IfNotLoop::testIfNot@34 (line 86)
ldr	x3, [x26, #24]              ;*laload {reexecute=0 rethrow=0 return_oop=0}
                                    ; - benchmarks.IfNotLoop::testIfNot@24 (line 85)
ldr	w11, [x23, #8]              ; implicit exception: dispatches to 0x000000011430bcb4
cmp	x24, #0xa
lsl	x21, x10, #3                ;*getfield root {reexecute=0 rethrow=0 return_oop=0}
                                    ; - clojure.lang.Var::getRawRoot@1 (line 260)
                                    ; - clj_code$test_if_not::invokeStatic@3 (line 11)
                                    ; - clj_code$test_if_not::invokePrim@1
                                    ; - benchmarks.IfNotLoop::testIfNot@34 (line 86)
csel	x10, x14, x15, lt  // lt = tstop
cmp	w11, w5
add	x4, x10, x4                 ;*ladd {reexecute=0 rethrow=0 return_oop=0}
                                    ; - benchmarks.IfNotLoop::testIfNot@39 (line 86)
b.ne	0x000000011430bc48  // b.any;*getfield root {reexecute=0 rethrow=0 return_oop=0}
                                    ; - clojure.lang.Var::getRawRoot@1 (line 260)
                                    ; - clj_code$test_if_not::invokeStatic@3 (line 11)
                                    ; - clj_code$test_if_not::invokePrim@1
                                    ; - benchmarks.IfNotLoop::testIfNot@34 (line 86)
cbz	x21, 0x000000011430bc24
cmp	x3, #0xa
ldr	w11, [x21, #8]
csel	x10, x14, x15, lt  // lt = tstop
cmp	w11, w1
b.ne	0x000000011430bc84  // b.any;*checkcast {reexecute=0 rethrow=0 return_oop=0}
                                    ; - clj_code$test_if_not::invokeStatic@6 (line 11)
                                    ; - clj_code$test_if_not::invokePrim@1
                                    ; - benchmarks.IfNotLoop::testIfNot@34 (line 86)
add	w16, w16, #0x2              ;*iinc {reexecute=0 rethrow=0 return_oop=0}
                                    ; - benchmarks.IfNotLoop::testIfNot@41 (line 85)
add	x4, x10, x4                 ;*ladd {reexecute=0 rethrow=0 return_oop=0}
                                    ; - benchmarks.IfNotLoop::testIfNot@39 (line 86)
cmp	w16, w19
b.lt	0x000000011430bb00  // b.tstop;*goto {reexecute=0 rethrow=0 return_oop=0}
                                    ; - benchmarks.IfNotLoop::testIfNot@44 (line 85)
ldr	x11, [x28, #896]            ; ImmutableOopMap {r12=Oop rbcp=Derived_oop_r12 r13=Derived_oop_r12 c_rarg0=Oop rmonitors=Oop }
                                    ;*goto {reexecute=1 rethrow=0 return_oop=0}
                                    ; - (reexecute) benchmarks.IfNotLoop::testIfNot@44 (line 85)
ldr	wzr, [x11]                  ;*goto {reexecute=0 rethrow=0 return_oop=0}
                                    ; - benchmarks.IfNotLoop::testIfNot@44 (line 85)
                                    ;   {poll}
cmp	w16, w2
b.lt	0x000000011430bae4  // b.tstop
cmp	w16, w17
b.ge	0x000000011430bbf8  // b.tcont
add	x11, x12, #0x1c
mov	x1, #0x370000              	// #3604480
