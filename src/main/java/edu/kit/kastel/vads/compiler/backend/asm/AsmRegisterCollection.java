package edu.kit.kastel.vads.compiler.backend.asm;

import java.util.List;

public class AsmRegisterCollection {
	public final List<AsmRegister> REGISTERS = List.of(
		new AsmRegister("RAX"),
		new AsmRegister("RBX"),
		new AsmRegister("RCX"),
		new AsmRegister("RDX"),
		new AsmRegister("RSI"),
		new AsmRegister("RDI"),
		new AsmRegister("R8"),
		new AsmRegister("R9"),
		new AsmRegister("R10"),
		new AsmRegister("R11"),
		new AsmRegister("R12"),
		new AsmRegister("R13"),
		new AsmRegister("R14"),
		new AsmRegister("R15")
	);	
}
