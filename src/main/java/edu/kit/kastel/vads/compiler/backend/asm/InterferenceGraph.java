package edu.kit.kastel.vads.compiler.backend.asm;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import edu.kit.kastel.vads.compiler.ir.node.Node;

/**
 * Represents liveness interference between variables.
 */
public class InterferenceGraph {

	private Map<Node, Set<Node>> adjList = new HashMap<>();

		public void addEdge(Node u, Node v) {
			if (!u.equals(v)) {
				adjList.computeIfAbsent(u, k -> new HashSet<>()).add(v);
				adjList.computeIfAbsent(v, k -> new HashSet<>()).add(u);
			}
		}

		public Set<Node> getNodes() {
			return adjList.keySet();
		}

		public int degree(Node node) {
			return adjList.get(node).size();
		} 

		public Set<Node> getAdjacents(Node node) {
			return adjList.getOrDefault(node, new HashSet<>());
		}

		public Map<Node, Set<Node>> getAdjList() {
			return adjList;
		}
	
}
