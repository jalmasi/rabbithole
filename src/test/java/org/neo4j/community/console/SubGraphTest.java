package org.neo4j.community.console;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.neo4j.driver.types.Node;

import java.util.Collections;
import java.util.Map;
import java.util.SortedMap;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author mh
 * @since 27.05.12
 */
public class SubGraphTest {

    private GraphDatabaseService gdb;
    private Node aNode;
    private Transaction tx;

    @Before
    public void setUp() throws Exception {
        gdb = new TestGraphDatabaseFactory().newImpermanentDatabase();
        tx = gdb.beginTx();
        aNode = gdb.createNode();
    }

    @After
    public void tearDown() throws Exception {
        try {
            tx.success();tx.close();
        } catch(Exception e) {}
        gdb.shutdown();
    }

    @Test
    public void testToMap() throws Exception {
        final Node node = gdb.createNode();
        final Map<String, Object> data = SubGraph.toMap(node);
        assertEquals(map("id",node.getId()), data);
    }

    @Test
    public void testFromSimpleCypherResult() throws Exception {
        final CypherQueryExecutor.CypherResult result = result("node", aNode);
        final SubGraph graph = SubGraph.from(gdb, result);
        assertRefNodeGraph(graph);
    }

    @Test
    public void testFromRelCypherResult() throws Exception {
        final Relationship rel = aNode.createRelationshipTo(aNode, DynamicRelationshipType.withName("REL"));
        final CypherQueryExecutor.CypherResult result = result("rel", rel);
        final SubGraph graph = SubGraph.from(gdb, result);
        assertEquals(1, graph.getNodes().size());
        final Map<Long, Map<String, Object>> rels = graph.getRelationships();
        assertEquals(1, rels.size());
        assertEquals(true, rels.containsKey(rel.getId()));
    }

    @Test
    public void testFromPathCypherResult() throws Exception {
        final Relationship rel = aNode.createRelationshipTo(aNode, DynamicRelationshipType.withName("REL"));
        final Path path = new PathImpl.Builder(aNode).push(rel).build();
        final CypherQueryExecutor.CypherResult result = result("path", path);
        final SubGraph graph = SubGraph.from(gdb, result);
        assertEquals(1, graph.getNodes().size());
        final Map<Long, Map<String, Object>> rels = graph.getRelationships();
        assertEquals(1, rels.size());
        assertEquals(true, rels.containsKey(rel.getId()));
    }

    private CypherQueryExecutor.CypherResult result(String column, Object value) {
        final Map<String, Object> row = Collections.singletonMap(column, value);
        return new CypherQueryExecutor.CypherResult(asList(column), asList(row), null, 0, null, null);
    }

    @Test
    public void testAddRelsWithInjectedNode() throws Exception {
        SubGraph graph = new SubGraph();
        final Relationship rel = mock(Relationship.class);
        final Node node1 = mockNode(1L);
        final Node node2 = mockNode(2L);
        final Node node3 = mockNode(3L);
        when(rel.getStartNode()).thenReturn((node1));
        when(rel.getEndNode()).thenReturn((node3));
        when(rel.getType()).thenReturn((DynamicRelationshipType.withName("TEST")));
        when(rel.getPropertyKeys()).thenReturn(Collections.EMPTY_LIST);
        graph.add(rel);
        graph.add(node2);
        assertEquals(3, graph.getNodes().size());
        final Map<String, Object> relData = Iterables.first(graph.getRelationshipsForViz().values());
        assertEquals(1L, relData.get("start"));
        assertEquals(3L, relData.get("end"));
        assertEquals(0, relData.get("source"));
        assertEquals(2, relData.get("target"));
    }

    private Node mockNode(long id) {
        final Node node1 = mock(Node.class);
        when(node1.getId()).thenReturn(id);
        when(node1.getPropertyKeys()).thenReturn(Collections.EMPTY_LIST);
        when(node1.getLabels()).thenReturn(new EmptyResourceIterable<Label>());
        return node1;
    }

    @Test
    public void testFromEmptyGraph() throws Exception {
        final SubGraph graph = SubGraph.from(gdb);
        assertRefNodeGraph(graph);
    }

    private void assertRefNodeGraph(SubGraph graph) {
        final Map<Long, Map<String, Object>> nodes = graph.getNodes();
        assertEquals(1, nodes.size());
        final Map<String, Object> node = nodes.get(0L);
        assertEquals(1, node.size());
        assertEquals(0L, node.get("id"));
    }

    @Test
    public void testFromRestResult() throws Exception {
        final Map<String, Object> restCypherResult = map("columns", asList("n", "r", "p"), "data", asList(map("n", node(0)), map("r", rel(0, 0, 1, "REL")), map("p", asList(node(0), node(0)))));
        final SubGraph graph = SubGraph.from(restCypherResult, true);
        final Map<Long, Map<String, Object>> nodes = graph.getNodes();
        assertEquals(2, nodes.size());
        assertEquals("node0", nodes.get(0L).get("name"));
        assertEquals(1, graph.getRelationships().size());
        assertEquals("REL", graph.getRelationships().get(0L).get("type"));

        final SubGraph graph2 = SubGraph.from(restCypherResult, false);
        assertEquals(1, graph2.getNodes().size());
        assertEquals(0, graph2.getRelationships().size());
    }

    @Test
    public void testImportSubGraph() throws Exception {
        final SubGraph graph = new SubGraph();
        graph.addNode(0L, map("name", "node0"));
        graph.addNode(10L, map("name", "node10"));
        graph.addRel(0L, map("name", "rel0", "start", 0L, "end", 10L, "type", "REL"));
        graph.importTo(gdb, db);
        assertEquals("node0", gdb.getNodeById(1).getProperty("name"));
        assertEquals("node10", gdb.getNodeById(2).getProperty("name"));
        final Relationship rel = gdb.getRelationshipById(0);
        assertEquals("rel0", rel.getProperty("name"));
        assertEquals("REL", rel.getType().name());
        assertEquals(2L, rel.getEndNode().getId());
        assertEquals(1L, rel.getStartNode().getId());
    }

    private Map rel(int id, int from, int to, final String type) {
        return map("self", "http://host:7474/db/data/relationships/" + id, "data", map("name", "rel" + id), "type", type, "start", nodeUri(from), "end", nodeUri(to));
    }

    private Map node(int id) {
        return map("self", nodeUri(id), "data", map("name", "node" + id));
    }

    private String nodeUri(int id) {
        return "http://host:7474/db/data/node/" + id;
    }

    @Test
    public void testMarkRelationshipsFromVariableLength() throws Exception {
        final Node n1 = gdb.createNode();
        final Node n0 = aNode;
        final Relationship relationship = n0.createRelationshipTo(n1, DynamicRelationshipType.withName("REL"));
        final SubGraph graph = SubGraph.from(gdb);
        final CypherQueryExecutor executor = new CypherQueryExecutor(gdb);
        final CypherQueryExecutor.CypherResult result = executor.cypherQuery("match (n) where id(n)=0 match (n)-[r*]-() return n,r", null);
        graph.markSelection(result);
        final Map<String, Object> nodeData = graph.getNodes().get(n0.getId());
        System.out.println("nodeData = " + nodeData);
        assertEquals(true, nodeData.containsKey("selected"));
        final Map<String, Object> relData = graph.getRelationships().get(relationship.getId());
        System.out.println("relData = " + relData);
        assertEquals(true, relData.containsKey("selected"));
    }

    @Test
    public void testFromSimpleGraph() throws Exception {
        final Node n1 = gdb.createNode();
        n1.setProperty("name", "Node1");
        final Node n0 = aNode;
        final Relationship relationship = n0.createRelationshipTo(n1, DynamicRelationshipType.withName("REL"));
        relationship.setProperty("related", true);
        final SubGraph graph = SubGraph.from(gdb);
        final Map<Long, Map<String, Object>> nodes = graph.getNodes();
        assertEquals(2, nodes.size());
        final Map<String, Object> node = nodes.get(n1.getId());
        assertEquals(2, node.size());
        assertEquals(n1.getId(), node.get("id"));
        assertEquals(n1.getProperty("name"), node.get("name"));
        final SortedMap<Long, org.neo4j.driver.types.Relationship> rels = graph.getRelationships();
        assertEquals(1, rels.size());
        final Map<String, Object> rel = rels.get(relationship.getId());
        assertEquals(7, rel.size());
        assertEquals(relationship.getId(), rel.get("id"));
        assertEquals(relationship.getProperty("related"), rel.get("related"));
        assertEquals(relationship.getType().name(), rel.get("type"));
        assertEquals(n0.getId(), rel.get("start"));
        assertEquals(n1.getId(), rel.get("end"));
        assertEquals(0, rel.get("source"));
        assertEquals(1, rel.get("target"));
    }
}
