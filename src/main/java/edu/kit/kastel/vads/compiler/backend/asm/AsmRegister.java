package edu.kit.kastel.vads.compiler.backend.asm;

import edu.kit.kastel.vads.compiler.backend.regalloc.Register;

public record AsmRegister(String name) implements Register {

	@Override
	public String toString() {
		return ("%" + this.name()).toLowerCase();
	}
}
