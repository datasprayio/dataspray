/*
 * Copyright 2024 Matus Faro
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.dataspray.store.util;

import com.google.common.graph.ElementOrder;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.NonNull;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@ApplicationScoped
public class CycleUtil {

    public interface Node {
        String getName();

        Set<String> getNodeInputs();

        Set<String> getNodeOutputs();

        static Node of(String name, Set<String> nodeInputs, Set<String> nodeOutputs) {
            return new NodeImpl(name, nodeInputs, nodeOutputs);
        }

        @Value
        class NodeImpl implements Node {
            @NonNull
            String name;
            @NonNull
            Set<String> nodeInputs;
            @NonNull
            Set<String> nodeOutputs;
        }
    }

    /**
     * Find a cycle in a graph of tasks with queues in-between. Each task having a set of input and output queues.
     *
     * @param nodes A list of tasks
     * @return If cycle detected, the list of tasks forming the cycle, otherwise empty
     */
    public <T extends Node> Optional<List<T>> findCycle(Collection<T> nodes) {
        MutableGraph<T> graph = GraphBuilder.directed()
                .nodeOrder(ElementOrder.unordered())
                .build();

        // Build graph
        for (T node : nodes) {
            graph.addNode(node);
            // Check for self-loops
            if (!Collections.disjoint(node.getNodeInputs(), node.getNodeOutputs())) {
                return Optional.of(Collections.singletonList(node)); // Self-loop detected
            }
        }

        for (T fromNode : nodes) {
            for (String outputQueue : fromNode.getNodeOutputs()) {
                for (T toNode : nodes) {
                    if (toNode.getNodeInputs().contains(outputQueue)) {
                        graph.putEdge(fromNode, toNode);
                    }
                }
            }
        }

        // Detect cycle using a more robust method
        return detectCycles(graph);
    }

    private <T extends Node> Optional<List<T>> detectCycles(MutableGraph<T> graph) {
        Set<T> visited = new HashSet<>();
        Deque<T> pathStack = new ArrayDeque<>();
        Set<T> pathSet = new HashSet<>();

        for (T node : graph.nodes()) {
            if (!visited.contains(node)) {
                if (findCycleUtil(node, graph, visited, pathStack, pathSet)) {
                    // Return the path stack as the detected cycle
                    return Optional.of(new ArrayList<>(pathStack));
                }
            }
        }
        return Optional.empty();
    }

    private <T extends Node> boolean findCycleUtil(T node, MutableGraph<T> graph, Set<T> visited, Deque<T> pathStack, Set<T> pathSet) {
        if (pathSet.contains(node)) {
            // Cycle detected, cut the cycle from the path stack
            List<T> cycle = new ArrayList<>();
            T current;
            do {
                current = pathStack.removeLast();
                cycle.add(current);
            } while (!current.equals(node));
            Collections.reverse(cycle);
            pathStack.clear();
            pathStack.addAll(cycle);
            return true;
        }
        if (visited.contains(node)) {
            return false;
        }

        visited.add(node);
        pathStack.addLast(node);
        pathSet.add(node);

        for (T neighbor : graph.successors(node)) {
            if (findCycleUtil(neighbor, graph, visited, pathStack, pathSet)) {
                return true;
            }
        }
        pathSet.remove(node);
        pathStack.removeLast();
        return false;
    }
}