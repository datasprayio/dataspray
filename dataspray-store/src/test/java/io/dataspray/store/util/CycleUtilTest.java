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

import com.google.common.collect.ImmutableSet;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Optional;
import java.util.Set;

import static io.dataspray.store.util.CycleUtil.Node;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class CycleUtilTest {

    @Inject
    CycleUtil cycleUtil;

    @Getter
    @AllArgsConstructor
    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    public enum TestCase {
        EMPTY(Set.of(), Optional.empty()),
        NO_CYCLE(Set.of(
                Node.of("A", Set.of("a"), Set.of("b")),
                Node.of("B", Set.of("b"), Set.of("c", "d")),
                Node.of("C", Set.of("a"), Set.of("e")),
                Node.of("D", Set.of("e"), Set.of("f"))
        ), Optional.empty()),
        CYCLE_SELF(Set.of(
                Node.of("A", Set.of("a"), Set.of("a")),
                Node.of("B", Set.of("b"), Set.of("c"))
        ), Optional.of(Set.of("A"))),
        CYCLE_HOP_ONE(Set.of(
                Node.of("A", Set.of("a"), Set.of("b")),
                Node.of("B", Set.of("b"), Set.of("a"))
        ), Optional.of(Set.of("A", "B"))),
        CYCLE_HOP_TWO(Set.of(
                Node.of("A", Set.of("a"), Set.of("b")),
                Node.of("B", Set.of("b"), Set.of("c")),
                Node.of("C", Set.of("c"), Set.of("a"))
        ), Optional.of(Set.of("A", "B", "C"))),
        TWO_CYCLES(Set.of(
                Node.of("A", Set.of("a"), Set.of("a", "b")),
                Node.of("B", Set.of("b"), Set.of("a"))
        ), Optional.of(Set.of("A")));
        Set<Node> inputGraph;
        Optional<Set<String>> expectedCycleNames;
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(TestCase.class)
    public void test(TestCase testCase) throws Exception {
        assertEquals(
                testCase.getExpectedCycleNames(),
                cycleUtil.findCycle(testCase.getInputGraph())
                        .map(cycle -> cycle.stream().map(Node::getName).collect(ImmutableSet.toImmutableSet())));
    }
}