import java.util.*;
import java.io.*;

public class EgrepV1 {
  //MACROS
  static final int CONCAT = 0xC04CA7;
  static final int ETOILE = 0xE7011E;
  static final int ALTERN = 0xA17E54;
  static final int PROTECTION = 0xBADDAD;
  static final int PARENTHESEOUVRANT = 0x16641664;
  static final int PARENTHESEFERMANT = 0x51515151;
  static final int DOT = 0xD07;
  
  //REGEX
  private static String regEx;
  
  //MAIN
  public static void main(String arg[]) {
    if (arg.length < 1) {
      System.err.println("Usage: java EgrepV1 <regex> [file]");
      System.err.println("  If no file is provided, reads from stdin");
      System.exit(1);
    }
    
    regEx = arg[0];
    
    try {
      // Step 1: Parse RegEx to Syntax Tree
      RegExTree tree = parse();
      
      // Step 2: Convert Syntax Tree to ε-NFA
      NDFAutomaton ndfa = step2_AhoUllman(tree);
      
      // Step 3: Convert ε-NFA to DFA
      DFAutomaton dfa = step3_SubsetConstruction(ndfa);
      
      // Step 4: Minimize DFA
      DFAutomaton minDfa = step4_MinimizeDFA(dfa);
      
      // Step 5: Search in file or stdin
      if (arg.length >= 2) {
        searchInFile(minDfa, arg[1]);
      } else {
        searchInStream(minDfa, new BufferedReader(new InputStreamReader(System.in)));
      }
      
    } catch (Exception e) {
      System.err.println("ERROR: " + e.getMessage());
      e.printStackTrace();
      System.exit(1);
    }
  }

  //STEP 1: FROM REGEX TO SYNTAX TREE
  private static RegExTree parse() throws Exception {
    ArrayList<RegExTree> result = new ArrayList<RegExTree>();
    for (int i=0; i<regEx.length(); i++) {
      result.add(new RegExTree(charToRoot(regEx.charAt(i)), new ArrayList<RegExTree>()));
    }
    return parse(result);
  }
  
  private static int charToRoot(char c) {
    if (c=='.') return DOT;
    if (c=='*') return ETOILE;
    if (c=='|') return ALTERN;
    if (c=='(') return PARENTHESEOUVRANT;
    if (c==')') return PARENTHESEFERMANT;
    return (int)c;
  }
  
  private static RegExTree parse(ArrayList<RegExTree> result) throws Exception {
    while (containParenthese(result)) result = processParenthese(result);
    while (containEtoile(result)) result = processEtoile(result);
    while (containConcat(result)) result = processConcat(result);
    while (containAltern(result)) result = processAltern(result);
    
    if (result.size() > 1) throw new Exception("Invalid regex");
    
    return removeProtection(result.get(0));
  }
  
  private static boolean containParenthese(ArrayList<RegExTree> trees) {
    for (RegExTree t: trees) if (t.root==PARENTHESEFERMANT || t.root==PARENTHESEOUVRANT) return true;
    return false;
  }
  
  private static ArrayList<RegExTree> processParenthese(ArrayList<RegExTree> trees) throws Exception {
    ArrayList<RegExTree> result = new ArrayList<RegExTree>();
    boolean found = false;
    for (RegExTree t: trees) {
      if (!found && t.root==PARENTHESEFERMANT) {
        boolean done = false;
        ArrayList<RegExTree> content = new ArrayList<RegExTree>();
        while (!done && !result.isEmpty()) {
          if (result.get(result.size()-1).root==PARENTHESEOUVRANT) {
            done = true;
            result.remove(result.size()-1);
          } else {
            content.add(0, result.remove(result.size()-1));
          }
        }
        if (!done) throw new Exception("Mismatched parentheses");
        found = true;
        ArrayList<RegExTree> subTrees = new ArrayList<RegExTree>();
        subTrees.add(parse(content));
        result.add(new RegExTree(PROTECTION, subTrees));
      } else {
        result.add(t);
      }
    }
    if (!found) throw new Exception("Mismatched parentheses");
    return result;
  }
  
  private static boolean containEtoile(ArrayList<RegExTree> trees) {
    for (RegExTree t: trees) if (t.root==ETOILE && t.subTrees.isEmpty()) return true;
    return false;
  }
  
  private static ArrayList<RegExTree> processEtoile(ArrayList<RegExTree> trees) throws Exception {
    ArrayList<RegExTree> result = new ArrayList<RegExTree>();
    boolean found = false;
    for (RegExTree t: trees) {
      if (!found && t.root==ETOILE && t.subTrees.isEmpty()) {
        if (result.isEmpty()) throw new Exception("Invalid use of *");
        found = true;
        RegExTree last = result.remove(result.size()-1);
        ArrayList<RegExTree> subTrees = new ArrayList<RegExTree>();
        subTrees.add(last);
        result.add(new RegExTree(ETOILE, subTrees));
      } else {
        result.add(t);
      }
    }
    return result;
  }
  
  private static boolean containConcat(ArrayList<RegExTree> trees) {
    boolean firstFound = false;
    for (RegExTree t: trees) {
      if (!firstFound && t.root!=ALTERN) { firstFound = true; continue; }
      if (firstFound) if (t.root!=ALTERN) return true; else firstFound = false;
    }
    return false;
  }
  
  private static ArrayList<RegExTree> processConcat(ArrayList<RegExTree> trees) throws Exception {
    ArrayList<RegExTree> result = new ArrayList<RegExTree>();
    boolean found = false;
    boolean firstFound = false;
    for (RegExTree t: trees) {
      if (!found && !firstFound && t.root!=ALTERN) {
        firstFound = true;
        result.add(t);
        continue;
      }
      if (!found && firstFound && t.root==ALTERN) {
        firstFound = false;
        result.add(t);
        continue;
      }
      if (!found && firstFound && t.root!=ALTERN) {
        found = true;
        RegExTree last = result.remove(result.size()-1);
        ArrayList<RegExTree> subTrees = new ArrayList<RegExTree>();
        subTrees.add(last);
        subTrees.add(t);
        result.add(new RegExTree(CONCAT, subTrees));
      } else {
        result.add(t);
      }
    }
    return result;
  }
  
  private static boolean containAltern(ArrayList<RegExTree> trees) {
    for (RegExTree t: trees) if (t.root==ALTERN && t.subTrees.isEmpty()) return true;
    return false;
  }
  
  private static ArrayList<RegExTree> processAltern(ArrayList<RegExTree> trees) throws Exception {
    ArrayList<RegExTree> result = new ArrayList<RegExTree>();
    boolean found = false;
    RegExTree gauche = null;
    boolean done = false;
    for (RegExTree t: trees) {
      if (!found && t.root==ALTERN && t.subTrees.isEmpty()) {
        if (result.isEmpty()) throw new Exception("Invalid use of |");
        found = true;
        gauche = result.remove(result.size()-1);
        continue;
      }
      if (found && !done) {
        if (gauche==null) throw new Exception("Invalid use of |");
        done = true;
        ArrayList<RegExTree> subTrees = new ArrayList<RegExTree>();
        subTrees.add(gauche);
        subTrees.add(t);
        result.add(new RegExTree(ALTERN, subTrees));
      } else {
        result.add(t);
      }
    }
    return result;
  }
  
  private static RegExTree removeProtection(RegExTree tree) throws Exception {
    if (tree.root==PROTECTION && tree.subTrees.size()!=1) throw new Exception("Invalid protection");
    if (tree.subTrees.isEmpty()) return tree;
    if (tree.root==PROTECTION) return removeProtection(tree.subTrees.get(0));
    
    ArrayList<RegExTree> subTrees = new ArrayList<RegExTree>();
    for (RegExTree t: tree.subTrees) subTrees.add(removeProtection(t));
    return new RegExTree(tree.root, subTrees);
  }

  //STEP 2: FROM SYNTAX TREE TO ε-NFA (Aho-Ullman/Thompson Construction)
  private static NDFAutomaton step2_AhoUllman(RegExTree ret) {
    if (ret.subTrees.isEmpty()) {
      int[][] tTab = new int[2][256];
      ArrayList<Integer>[] eTab = new ArrayList[2];
      
      for (int i=0; i<tTab.length; i++) for (int col=0; col<256; col++) tTab[i][col]=-1;
      for (int i=0; i<eTab.length; i++) eTab[i]=new ArrayList<Integer>();
      
      if (ret.root!=DOT) tTab[0][ret.root]=1;
      else for (int i=0; i<256; i++) tTab[0][i]=1;
      
      return new NDFAutomaton(tTab, eTab);
    }
    
    if (ret.root==CONCAT) {
      NDFAutomaton gauche = step2_AhoUllman(ret.subTrees.get(0));
      int[][] tTab_g = gauche.transitionTable;
      ArrayList<Integer>[] eTab_g = gauche.epsilonTransitionTable;
      NDFAutomaton droite = step2_AhoUllman(ret.subTrees.get(1));
      int[][] tTab_d = droite.transitionTable;
      ArrayList<Integer>[] eTab_d = droite.epsilonTransitionTable;
      int lg=tTab_g.length;
      int ld=tTab_d.length;
      int[][] tTab = new int[lg+ld][256];
      ArrayList<Integer>[] eTab = new ArrayList[lg+ld];

      for (int i=0; i<tTab.length; i++) for (int col=0; col<256; col++) tTab[i][col]=-1;
      for (int i=0; i<eTab.length; i++) eTab[i]=new ArrayList<Integer>();

      eTab[lg-1].add(lg);

      for (int i=0; i<lg; i++) for (int col=0; col<256; col++) tTab[i][col]=tTab_g[i][col];
      for (int i=0; i<lg; i++) eTab[i].addAll(eTab_g[i]);
      for (int i=lg; i<lg+ld-1; i++) for (int col=0; col<256; col++) if (tTab_d[i-lg][col]!=-1) tTab[i][col]=tTab_d[i-lg][col]+lg;
      for (int i=lg; i<lg+ld-1; i++) for (int s: eTab_d[i-lg]) eTab[i].add(s+lg);

      return new NDFAutomaton(tTab, eTab);
    }

    if (ret.root==ALTERN) {
      NDFAutomaton gauche = step2_AhoUllman(ret.subTrees.get(0));
      int[][] tTab_g = gauche.transitionTable;
      ArrayList<Integer>[] eTab_g = gauche.epsilonTransitionTable;
      NDFAutomaton droite = step2_AhoUllman(ret.subTrees.get(1));
      int[][] tTab_d = droite.transitionTable;
      ArrayList<Integer>[] eTab_d = droite.epsilonTransitionTable;
      int lg=tTab_g.length;
      int ld=tTab_d.length;
      int[][] tTab = new int[2+lg+ld][256];
      ArrayList<Integer>[] eTab = new ArrayList[2+lg+ld];

      for (int i=0; i<tTab.length; i++) for (int col=0; col<256; col++) tTab[i][col]=-1;
      for (int i=0; i<eTab.length; i++) eTab[i]=new ArrayList<Integer>();

      eTab[0].add(1);
      eTab[0].add(1+lg);
      eTab[1+lg-1].add(2+lg+ld-1);
      eTab[1+lg+ld-1].add(2+lg+ld-1);

      for (int i=1; i<1+lg; i++) for (int col=0; col<256; col++) if (tTab_g[i-1][col]!=-1) tTab[i][col]=tTab_g[i-1][col]+1;
      for (int i=1; i<1+lg; i++) for (int s: eTab_g[i-1]) eTab[i].add(s+1);
      for (int i=1+lg; i<1+lg+ld-1; i++) for (int col=0; col<256; col++) if (tTab_d[i-1-lg][col]!=-1) tTab[i][col]=tTab_d[i-1-lg][col]+1+lg;
      for (int i=1+lg; i<1+lg+ld; i++) for (int s: eTab_d[i-1-lg]) eTab[i].add(s+1+lg);

      return new NDFAutomaton(tTab, eTab);
    }

    if (ret.root==ETOILE) {
      NDFAutomaton fils = step2_AhoUllman(ret.subTrees.get(0));
      int[][] tTab_fils = fils.transitionTable;
      ArrayList<Integer>[] eTab_fils = fils.epsilonTransitionTable;
      int l=tTab_fils.length;
      int[][] tTab = new int[2+l][256];
      ArrayList<Integer>[] eTab = new ArrayList[2+l];

      for (int i=0; i<tTab.length; i++) for (int col=0; col<256; col++) tTab[i][col]=-1;
      for (int i=0; i<eTab.length; i++) eTab[i]=new ArrayList<Integer>();

      eTab[0].add(1);
      eTab[0].add(2+l-1);
      eTab[2+l-2].add(2+l-1);
      eTab[2+l-2].add(1);

      for (int i=1; i<2+l-1; i++) for (int col=0; col<256; col++) if (tTab_fils[i-1][col]!=-1) tTab[i][col]=tTab_fils[i-1][col]+1;
      for (int i=1; i<2+l-1; i++) for (int s: eTab_fils[i-1]) eTab[i].add(s+1);

      return new NDFAutomaton(tTab, eTab);
    }

    return null;
  }

  //STEP 3: FROM ε-NFA TO DFA (Subset Construction)
  private static DFAutomaton step3_SubsetConstruction(NDFAutomaton ndfa) {
    // Map from set of NFA states to DFA state number
    HashMap<Set<Integer>, Integer> stateMap = new HashMap<>();
    ArrayList<Set<Integer>> dfaStates = new ArrayList<>();
    Queue<Integer> unmarked = new LinkedList<>();
    
    // Start with ε-closure of initial state 0
    Set<Integer> initialClosure = epsilonClosure(ndfa, Collections.singleton(0));
    stateMap.put(initialClosure, 0);
    dfaStates.add(initialClosure);
    unmarked.add(0);
    
    int ndfaFinalState = ndfa.transitionTable.length - 1;
    
    // Build DFA transition table
    ArrayList<HashMap<Integer, Integer>> dfaTransitions = new ArrayList<>();
    dfaTransitions.add(new HashMap<>());
    
    while (!unmarked.isEmpty()) {
      int currentDfaState = unmarked.poll();
      Set<Integer> currentNfaStates = dfaStates.get(currentDfaState);
      
      // Try all possible input characters
      for (int c = 0; c < 256; c++) {
        Set<Integer> nextStates = new HashSet<>();
        
        // Find all states reachable via character c
        for (int nfaState : currentNfaStates) {
          if (ndfa.transitionTable[nfaState][c] != -1) {
            nextStates.add(ndfa.transitionTable[nfaState][c]);
          }
        }
        
        if (!nextStates.isEmpty()) {
          // Compute ε-closure of the result
          Set<Integer> closure = epsilonClosure(ndfa, nextStates);
          
          // Check if this DFA state already exists
          if (!stateMap.containsKey(closure)) {
            int newState = dfaStates.size();
            stateMap.put(closure, newState);
            dfaStates.add(closure);
            unmarked.add(newState);
            dfaTransitions.add(new HashMap<>());
          }
          
          int targetDfaState = stateMap.get(closure);
          dfaTransitions.get(currentDfaState).put(c, targetDfaState);
        }
      }
    }
    
    // Convert to array format
    int[][] transitionTable = new int[dfaStates.size()][256];
    for (int i = 0; i < transitionTable.length; i++) {
      for (int c = 0; c < 256; c++) {
        transitionTable[i][c] = dfaTransitions.get(i).getOrDefault(c, -1);
      }
    }
    
    // Determine accepting states
    boolean[] acceptingStates = new boolean[dfaStates.size()];
    for (int i = 0; i < dfaStates.size(); i++) {
      acceptingStates[i] = dfaStates.get(i).contains(ndfaFinalState);
    }
    
    return new DFAutomaton(transitionTable, acceptingStates);
  }
  
  // Compute ε-closure of a set of states
  private static Set<Integer> epsilonClosure(NDFAutomaton ndfa, Set<Integer> states) {
    Set<Integer> closure = new HashSet<>(states);
    Stack<Integer> stack = new Stack<>();
    stack.addAll(states);
    
    while (!stack.isEmpty()) {
      int state = stack.pop();
      for (int nextState : ndfa.epsilonTransitionTable[state]) {
        if (!closure.contains(nextState)) {
          closure.add(nextState);
          stack.push(nextState);
        }
      }
    }
    
    return closure;
  }

  //STEP 4: MINIMIZE DFA (Hopcroft's Algorithm)
  private static DFAutomaton step4_MinimizeDFA(DFAutomaton dfa) {
    int n = dfa.transitionTable.length;
    
    // Initial partition: accepting vs non-accepting states
    List<Set<Integer>> partitions = new ArrayList<>();
    Set<Integer> accepting = new HashSet<>();
    Set<Integer> nonAccepting = new HashSet<>();
    
    for (int i = 0; i < n; i++) {
      if (dfa.acceptingStates[i]) {
        accepting.add(i);
      } else {
        nonAccepting.add(i);
      }
    }
    
    if (!accepting.isEmpty()) partitions.add(accepting);
    if (!nonAccepting.isEmpty()) partitions.add(nonAccepting);
    
    // Refine partitions
    boolean changed = true;
    while (changed) {
      changed = false;
      List<Set<Integer>> newPartitions = new ArrayList<>();
      
      for (Set<Integer> partition : partitions) {
        if (partition.size() <= 1) {
          newPartitions.add(partition);
          continue;
        }
        
        // Try to split this partition
        Map<String, Set<Integer>> splits = new HashMap<>();
        
        for (int state : partition) {
          // Create signature: for each character, which partition does it go to?
          StringBuilder signature = new StringBuilder();
          for (int c = 0; c < 256; c++) {
            int target = dfa.transitionTable[state][c];
            if (target == -1) {
              signature.append("-1,");
            } else {
              // Find which partition the target belongs to
              int partitionIndex = -1;
              for (int p = 0; p < partitions.size(); p++) {
                if (partitions.get(p).contains(target)) {
                  partitionIndex = p;
                  break;
                }
              }
              signature.append(partitionIndex).append(",");
            }
          }
          
          String sig = signature.toString();
          splits.putIfAbsent(sig, new HashSet<>());
          splits.get(sig).add(state);
        }
        
        if (splits.size() > 1) {
          changed = true;
        }
        newPartitions.addAll(splits.values());
      }
      
      partitions = newPartitions;
    }
    
    // Build minimized DFA
    Map<Integer, Integer> stateToPartition = new HashMap<>();
    for (int i = 0; i < partitions.size(); i++) {
      for (int state : partitions.get(i)) {
        stateToPartition.put(state, i);
      }
    }
    
    int[][] newTransitionTable = new int[partitions.size()][256];
    boolean[] newAcceptingStates = new boolean[partitions.size()];
    
    for (int i = 0; i < partitions.size(); i++) {
      int representative = partitions.get(i).iterator().next();
      newAcceptingStates[i] = dfa.acceptingStates[representative];
      
      for (int c = 0; c < 256; c++) {
        int target = dfa.transitionTable[representative][c];
        if (target == -1) {
          newTransitionTable[i][c] = -1;
        } else {
          newTransitionTable[i][c] = stateToPartition.get(target);
        }
      }
    }
    
    // Make sure initial state (0) maps to partition 0
    int initialPartition = stateToPartition.get(0);
    if (initialPartition != 0) {
      // Swap partitions
      int[] tempTrans = newTransitionTable[0];
      newTransitionTable[0] = newTransitionTable[initialPartition];
      newTransitionTable[initialPartition] = tempTrans;
      
      boolean tempAccept = newAcceptingStates[0];
      newAcceptingStates[0] = newAcceptingStates[initialPartition];
      newAcceptingStates[initialPartition] = tempAccept;
      
      // Update all transitions
      for (int i = 0; i < newTransitionTable.length; i++) {
        for (int c = 0; c < 256; c++) {
          if (newTransitionTable[i][c] == 0) {
            newTransitionTable[i][c] = initialPartition;
          } else if (newTransitionTable[i][c] == initialPartition) {
            newTransitionTable[i][c] = 0;
          }
        }
      }
    }
    
    return new DFAutomaton(newTransitionTable, newAcceptingStates);
  }

  //STEP 5: SEARCH IN FILE
  private static void searchInFile(DFAutomaton dfa, String filename) throws IOException {
    BufferedReader reader = new BufferedReader(new FileReader(filename));
    searchInStream(dfa, reader);
  }
  
  private static void searchInStream(DFAutomaton dfa, BufferedReader reader) throws IOException {
    String line;
    int lineNumber = 0;
    
    while ((line = reader.readLine()) != null) {
      lineNumber++;
      
      // Check if pattern accepts empty string (initial state is accepting)
      // If so, every line matches
      if (dfa.acceptingStates[0]) {
        System.out.println(line);
        continue;
      }
      
      // Check if any suffix of the line matches
      boolean found = false;
      for (int i = 0; i < line.length(); i++) {
        if (matchesFromPosition(dfa, line, i)) {
          found = true;
          break;
        }
      }
      
      if (found) {
        System.out.println(line);
      }
    }
    
    reader.close();
  }
  
  private static boolean matchesFromPosition(DFAutomaton dfa, String text, int start) {
    int state = 0;
    
    for (int i = start; i < text.length(); i++) {
      char c = text.charAt(i);
      
      // Handle non-ASCII characters (treat as non-matching)
      if (c >= 256) {
        return false;
      }
      
      int nextState = dfa.transitionTable[state][c];
      
      if (nextState == -1) {
        return false;
      }
      
      state = nextState;
      
      // Check if we've reached an accepting state
      if (dfa.acceptingStates[state]) {
        return true;
      }
    }
    
    // Check if final state is accepting
    return dfa.acceptingStates[state];
  }
}

//UTILITY CLASSES
class RegExTree {
  protected int root;
  protected ArrayList<RegExTree> subTrees;
  
  public RegExTree(int root, ArrayList<RegExTree> subTrees) {
    this.root = root;
    this.subTrees = subTrees;
  }
}

class NDFAutomaton {
  protected int[][] transitionTable;
  protected ArrayList<Integer>[] epsilonTransitionTable;
  
  public NDFAutomaton(int[][] transitionTable, ArrayList<Integer>[] epsilonTransitionTable) {
    this.transitionTable = transitionTable;
    this.epsilonTransitionTable = epsilonTransitionTable;
  }
}

class DFAutomaton {
  protected int[][] transitionTable;
  protected boolean[] acceptingStates;
  
  public DFAutomaton(int[][] transitionTable, boolean[] acceptingStates) {
    this.transitionTable = transitionTable;
    this.acceptingStates = acceptingStates;
  }
}
