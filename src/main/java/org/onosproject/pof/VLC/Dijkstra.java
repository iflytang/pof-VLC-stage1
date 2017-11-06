package org.onosproject.pof.VLC;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Created by tsf on 11/4/17.
 *
 * @Description with Dijkstra Algorithm to calculate the shortest path in the network.
 */


public class Dijkstra {
    /**
     * class Vertex defines the vertex in the Graph
     */
    class Vertex implements Comparable<Vertex> {
        private Integer id;        // the id of vertex
        private Integer distance;  // the distance between vertices

        public Vertex(Integer id, Integer distance) {
            super();
            this.id = id;
            this.distance = distance;
        }

        public Integer getId() {
            return this.id;
        }

        public Integer getDistance() {
            return this.distance;
        }

        public void setId(Integer id) {
            this.id = id;
        }

        public void setDistance(Integer distance) {
            this.distance = distance;
        }

        @Override
        public int hashCode() {
           final int prime = 31;
           int result = 1;
           result = prime * result + (this.distance == null ? 0 : this.distance.hashCode());
           result = prime * result + (this.id == null ? 0 : this.id.hashCode());
           return result;
        }

        @Override
        public boolean equals(Object obj) {
            if(this == obj)
                return true;
            if(obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;

            Vertex other = (Vertex) obj;
            if(this.distance == null) {
                if(other.distance != null)
                    return false;
            } else if(!this.distance.equals(other.distance)) {
                return false;
            }
            if(this.id == null) {
                if(other.id != null)
                    return false;
            } else if(!this.id.equals(other.id)) {
                return false;
            }
            return true;
        }

        // override the compareTo function to implement Comparable<Vertex>
        @Override
        public int compareTo(Vertex other) {
            if(this.distance < other.distance)
                return -1;
            else if(this.distance > other.distance)
                return 1;
            else
                return this.getId().compareTo(other.getId());
        }

        @Override
        public String toString() {
            return "Vertex[id = " + id + ", distance = " + distance + "]";
        }
    }

    /**
     * implement Dijkstra Algorithm, add default topology, return the shortest path
     */
    private final Map<Integer, List<Vertex>> vertices;     // store graph of topology in vertices

    //TODO add default topology here
    public Dijkstra() {
        this.vertices = new HashMap<>();
        // add default topology here
        this.addVertex(1, Arrays.asList(this.new Vertex(2, 1), this.new Vertex(3, 1)));
        this.addVertex(2, Arrays.asList(this.new Vertex(1, 1), this.new Vertex(3, 1), this.new Vertex(4, 1)));
        this.addVertex(3, Arrays.asList(this.new Vertex(1, 1), this.new Vertex(2, 1), this.new Vertex(5, 1)));
        this.addVertex(4, Arrays.asList(this.new Vertex(2, 1), this.new Vertex(5, 1), this.new Vertex(6, 1)));
        this.addVertex(5, Arrays.asList(this.new Vertex(3, 1), this.new Vertex(4, 1), this.new Vertex(6, 1)));
        this.addVertex(6, Arrays.asList(this.new Vertex(4, 1), this.new Vertex(5, 1)));
    }

    public Map<Integer, List<Vertex>> getVertices() {
        return this.vertices;
    }

    public void addVertex(Integer integer, List<Vertex> vertex) {
        this.vertices.put(integer, vertex);
    }

    // return the shortest path as List<Integer> from src vertex to dst vertex
    public List<Integer> getShortestPath(Integer src, Integer dst) {
        final Map<Integer, Integer> distances = new HashMap<>();  // store id and distance
        final Map<Integer, Vertex> previous = new HashMap<>();    // store the previous id and distance
        PriorityQueue<Vertex> nodes = new PriorityQueue<>();      // store the topology's vertices

        // add vertex into nodes, if src, set distance as zero
        for(Integer vertex : this.vertices.keySet()) {
            if(vertex == src) {
                distances.put(vertex, 0);
                nodes.add(new Vertex(vertex, 0));
            } else {
                distances.put(vertex, Integer.MAX_VALUE);
                nodes.add(new Vertex(vertex, Integer.MAX_VALUE));
            }
            previous.put(vertex, null);   //store vertex chain for every node, just like {1, Vertex[2, 1]}
        }

        // get shortest path from src to dst
        while (!nodes.isEmpty()) {
            // if smallest == dst, return path
            Vertex smallest = nodes.poll();
            if(smallest.getId() == dst) {
                final List<Integer> path = new ArrayList<>();
                while (previous.get(smallest.getId()) != null) {
                    path.add(smallest.getId());
                    smallest = previous.get(smallest.getId());   // previous just like {1, Vertex[2, 1]}
                }
                path.add(src);
                Collections.reverse(path);   // return a list from src to dst
                return path;
            }

            // processing
            if (distances.get(smallest.getId()) == Integer.MAX_VALUE) {
                break;   // start processing from src node
            }
            for (Vertex neighbor : vertices.get(smallest.getId())) {
                Integer alt = distances.get(smallest.getId()) + neighbor.getDistance();
                if(alt < distances.get(neighbor.getId())) {
                    // keep the local distance (per hop) between vertices shortest
                    distances.put(neighbor.getId(), alt);    // update distance
                    previous.put(neighbor.getId(), smallest); // store in links

                    forloop:
                    for (Vertex n : nodes) {
                        if (n.getId() == neighbor.getId()) {
                            nodes.remove(n);
                            n.setDistance(alt);   // update the newest distance in node queue
                            nodes.add(n);
                            break forloop;
                        }
                    }
                }
            }
        }

        // if no results
        List<Integer> list = new ArrayList<>(distances.keySet());
        return list;
    }
}
