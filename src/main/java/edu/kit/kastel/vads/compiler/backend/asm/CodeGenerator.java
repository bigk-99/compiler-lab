package edu.kit.kastel.vads.compiler.backend.asm;

import edu.kit.kastel.vads.compiler.backend.regalloc.Register;
import edu.kit.kastel.vads.compiler.ir.IrGraph;
import edu.kit.kastel.vads.compiler.ir.node.AddNode;
import edu.kit.kastel.vads.compiler.ir.node.BinaryOperationNode;
import edu.kit.kastel.vads.compiler.ir.node.Block;
import edu.kit.kastel.vads.compiler.ir.node.ConstIntNode;
import edu.kit.kastel.vads.compiler.ir.node.DivNode;
import edu.kit.kastel.vads.compiler.ir.node.ModNode;
import edu.kit.kastel.vads.compiler.ir.node.MulNode;
import edu.kit.kastel.vads.compiler.ir.node.Node;
import edu.kit.kastel.vads.compiler.ir.node.Phi;
import edu.kit.kastel.vads.compiler.ir.node.ProjNode;
import edu.kit.kastel.vads.compiler.ir.node.ReturnNode;
import edu.kit.kastel.vads.compiler.ir.node.StartNode;
import edu.kit.kastel.vads.compiler.ir.node.SubNode;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static edu.kit.kastel.vads.compiler.ir.util.NodeSupport.predecessorSkipProj;

public class CodeGenerator {
	
	public String generateCode(List<IrGraph> program) {
		StringBuilder builder = new StringBuilder();
		for (IrGraph graph : program) {
			AsmRegisterAllocator allocator = new AsmRegisterAllocator();
			Map<Node, Register> registers = allocator.allocateRegisters(graph);
			for (var entry : registers.entrySet()) {
				System.out.println("Node " + entry.getKey().toString() + " → " + entry.getValue());
			}
			builder.append(".global main")
				.append("\n")
				.append(".global _main")
				.append("\n")
				.append(".text")
				.append("\n\n");

			builder.append("main:")
				.append("\n")
				.append("call _main")
				.append("\n\n");

			builder.append("movq %rax, %rdi")
				.append("\n")
				.append("movq $0x3C, %rax")
				.append("\n")
				.append("syscall")
				.append("\n\n\n");

			builder.append("_main:")
				.append("\n");

			generateForGraph(graph, builder, registers);
			builder.append("\n");
		}

		return builder.toString();
	}

	private void generateForGraph(IrGraph graph, StringBuilder builder, Map<Node, Register> registers) {
		Set<Node> visited = new HashSet<>();
		scan(graph.endBlock(), visited, builder, registers);
	}

	private void scan(Node node, Set<Node> visited, StringBuilder builder, Map<Node, Register> registers) {
		for (Node predecessor : node.predecessors()) {
			if (visited.add(predecessor)) {
				scan(predecessor, visited, builder, registers);
			}
		}

		switch (node) {
			case AddNode add -> binary(builder, registers, add, "add");
			case SubNode sub -> binary(builder, registers, sub, "sub");
			case MulNode mul -> binary(builder, registers, mul, "imul");
			case DivNode div -> binary(builder, registers, div, "div");
			case ModNode mod -> builder.repeat(" ", 2)
				.append("")
				.append("\n")
				.append(0)
				.append("\n");
			case ReturnNode r -> builder.repeat(" ", 2).append("ret ");
				//.append(registers.get(predecessorSkipProj(r, ReturnNode.RESULT)));
            case ConstIntNode c -> builder.repeat(" ", 2)
                .append("movq")
				.append(" ")
				.append("$" + c.value())
				.append(", ")
				.append(registers.get(c));
			//case ConstIntNode c -> binaryAssignment(builder, registers, c, "mov");
            case Phi _ -> throw new UnsupportedOperationException("phi");
            case Block _, ProjNode _, StartNode _ -> {
                // do nothing, skip line break
                return;
			}
			default -> { return; }

		}
		builder.append("\n");
	}

	private static void binary(
        StringBuilder builder,
        Map<Node, Register> registers,
        BinaryOperationNode node,
        String opcode
    ) {
		builder.repeat(" ", 2)
			.append(opcode)
			.append(" ")
			.append(registers.get(predecessorSkipProj(node, BinaryOperationNode.LEFT)))
			.append(", ")
			.append(registers.get(predecessorSkipProj(node, BinaryOperationNode.RIGHT)));
    }
}
