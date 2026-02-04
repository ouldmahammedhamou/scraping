public class EgrepV1 {
  static final int C=0xC04CA7,E=0xE7011E,A=0xA17E54,P=0xBADDAD,PO=0x16641664,PF=0x51515151,D=0xD07,PL=0xE7011F;
  private static String regEx;
  public static void main(String[] arg) {
    if (arg.length<1) { System.err.println("Usage: java EgrepV1 <regex> [file]"); System.exit(1); }
    regEx=arg[0];
    try {
      RegExTree t=parse(); NDFAutomaton n=step2(t); DFAutomaton d=step4(step3(n));
      if (arg.length>=2) searchFile(d,arg[1]); else searchStream(d,new java.io.BufferedReader(new java.io.InputStreamReader(System.in)));
    } catch (Exception e) { System.err.println("ERROR: "+e.getMessage()); e.printStackTrace(); System.exit(1); }
  }
  private static RegExTree parse() throws Exception {
    java.util.ArrayList<RegExTree> L=new java.util.ArrayList<>(); for (int i=0;i<regEx.length();i++) L.add(new RegExTree(ch(regEx.charAt(i)),new java.util.ArrayList<>())); return parse(L);
  }
  private static int ch(char c) { if (c=='.') return D; if (c=='*') return E; if (c=='+') return PL; if (c=='|') return A; if (c=='(') return PO; if (c==')') return PF; return c; }
  private static RegExTree parse(java.util.ArrayList<RegExTree> L) throws Exception {
    while (has(L,PF)||has(L,PO)) L=paren(L); while (has(L,E)) L=op(L,E); while (has(L,PL)) L=op(L,PL); while (hasCat(L)) L=cat(L); while (has(L,A)) L=alt(L);
    if (L.size()!=1) throw new Exception("Invalid regex"); return unprot(L.get(0));
  }
  private static boolean has(java.util.ArrayList<RegExTree> L,int r) { for (RegExTree t:L) if (t.root==r&&t.subTrees.isEmpty()) return true; return false; }
  private static boolean hasCat(java.util.ArrayList<RegExTree> L) { boolean f=false; for (RegExTree t:L) { if (!f&&t.root!=A) f=true; else if (f&&t.root!=A) return true; else if (f) f=false; } return false; }
  private static java.util.ArrayList<RegExTree> paren(java.util.ArrayList<RegExTree> L) throws Exception {
    java.util.ArrayList<RegExTree> R=new java.util.ArrayList<>(),C; for (RegExTree t:L) {
      if (t.root!=PF) { R.add(t); continue; } C=new java.util.ArrayList<>(); while (!R.isEmpty()&&R.get(R.size()-1).root!=PO) C.add(0,R.remove(R.size()-1));
      if (R.isEmpty()) throw new Exception("Mismatched parentheses"); R.remove(R.size()-1); java.util.ArrayList<RegExTree> one=new java.util.ArrayList<>(); one.add(parse(C)); R.add(new RegExTree(P,one));
    } return R;
  }
  private static java.util.ArrayList<RegExTree> op(java.util.ArrayList<RegExTree> L,int op) throws Exception {
    java.util.ArrayList<RegExTree> R=new java.util.ArrayList<>(); for (RegExTree t:L) { if (t.root==op&&t.subTrees.isEmpty()) { if (R.isEmpty()) throw new Exception("Invalid op"); java.util.ArrayList<RegExTree> one=new java.util.ArrayList<>(); one.add(R.remove(R.size()-1)); R.add(new RegExTree(op,one)); } else R.add(t); } return R;
  }
  private static java.util.ArrayList<RegExTree> cat(java.util.ArrayList<RegExTree> L) throws Exception {
    java.util.ArrayList<RegExTree> R=new java.util.ArrayList<>(); boolean f=false,ff=false; for (RegExTree t:L) { if (!f&&!ff&&t.root!=A) { ff=true; R.add(t); } else if (!f&&ff&&t.root==A) { ff=false; R.add(t); } else if (!f&&ff&&t.root!=A) { f=true; RegExTree l=R.remove(R.size()-1); R.add(new RegExTree(C,new java.util.ArrayList<>(java.util.Arrays.asList(l,t)))); } else R.add(t); } return R;
  }
  private static java.util.ArrayList<RegExTree> alt(java.util.ArrayList<RegExTree> L) throws Exception {
    java.util.ArrayList<RegExTree> R=new java.util.ArrayList<>(); RegExTree g=null; boolean fd=false,done=false; for (RegExTree t:L) { if (!fd&&t.root==A&&t.subTrees.isEmpty()) { if (R.isEmpty()) throw new Exception("Invalid |"); g=R.remove(R.size()-1); fd=true; } else if (fd&&!done) { java.util.ArrayList<RegExTree> s=new java.util.ArrayList<>(); s.add(g); s.add(t); R.add(new RegExTree(A,s)); done=true; } else R.add(t); } return R;
  }
  private static RegExTree unprot(RegExTree t) throws Exception { if (t.root==P&&t.subTrees.size()!=1) throw new Exception("Invalid protection"); if (t.subTrees.isEmpty()) return t; if (t.root==P) return unprot(t.subTrees.get(0)); java.util.ArrayList<RegExTree> s=new java.util.ArrayList<>(); for (RegExTree c:t.subTrees) s.add(unprot(c)); return new RegExTree(t.root,s); }
  private static NDFAutomaton step2(RegExTree t) {
    if (t.subTrees.isEmpty()) { int[][] tt=new int[2][256]; @SuppressWarnings("unchecked") java.util.ArrayList<Integer>[] et=new java.util.ArrayList[2]; et[0]=new java.util.ArrayList<>(); et[1]=new java.util.ArrayList<>(); for (int i=0;i<256;i++) tt[0][i]=-1; if (t.root!=D) tt[0][t.root]=1; else for (int i=0;i<256;i++) tt[0][i]=1; return new NDFAutomaton(tt,et); }
    if (t.root==C) { NDFAutomaton L=step2(t.subTrees.get(0)),R=step2(t.subTrees.get(1)); int lg=L.transitionTable.length,ld=R.transitionTable.length; int[][] tt=new int[lg+ld][256]; @SuppressWarnings("unchecked") java.util.ArrayList<Integer>[] et=new java.util.ArrayList[lg+ld]; for (int i=0;i<lg+ld;i++) { et[i]=new java.util.ArrayList<>(); for (int c=0;c<256;c++) tt[i][c]=-1; } for (int i=0;i<lg;i++) { for (int c=0;c<256;c++) tt[i][c]=L.transitionTable[i][c]; et[i].addAll(L.epsilonTransitionTable[i]); } et[lg-1].add(lg); for (int i=lg;i<lg+ld-1;i++) for (int c=0;c<256;c++) if (R.transitionTable[i-lg][c]!=-1) tt[i][c]=R.transitionTable[i-lg][c]+lg; for (int i=lg;i<lg+ld-1;i++) for (int s:R.epsilonTransitionTable[i-lg]) et[i].add(s+lg); return new NDFAutomaton(tt,et); }
    if (t.root==A) { NDFAutomaton L=step2(t.subTrees.get(0)),R=step2(t.subTrees.get(1)); int lg=L.transitionTable.length,ld=R.transitionTable.length; int[][] tt=new int[2+lg+ld][256]; @SuppressWarnings("unchecked") java.util.ArrayList<Integer>[] et=new java.util.ArrayList[2+lg+ld]; for (int i=0;i<2+lg+ld;i++) { et[i]=new java.util.ArrayList<>(); for (int c=0;c<256;c++) tt[i][c]=-1; } et[0].add(1); et[0].add(1+lg); et[lg].add(2+lg+ld-1); et[1+lg+ld-1].add(2+lg+ld-1); for (int i=1;i<=lg;i++) { for (int c=0;c<256;c++) if (L.transitionTable[i-1][c]!=-1) tt[i][c]=L.transitionTable[i-1][c]+1; for (int s:L.epsilonTransitionTable[i-1]) et[i].add(s+1); } for (int i=1+lg;i<1+lg+ld;i++) { for (int c=0;c<256;c++) if (R.transitionTable[i-1-lg][c]!=-1) tt[i][c]=R.transitionTable[i-1-lg][c]+1+lg; for (int s:R.epsilonTransitionTable[i-1-lg]) et[i].add(s+1+lg); } return new NDFAutomaton(tt,et); }
    if (t.root==E||t.root==PL) { NDFAutomaton F=step2(t.subTrees.get(0)); int l=F.transitionTable.length; int[][] tt=new int[2+l][256]; @SuppressWarnings("unchecked") java.util.ArrayList<Integer>[] et=new java.util.ArrayList[2+l]; for (int i=0;i<2+l;i++) { et[i]=new java.util.ArrayList<>(); for (int c=0;c<256;c++) tt[i][c]=-1; } et[0].add(1); if (t.root==E) et[0].add(1+l); et[l].add(1+l); et[l].add(1); for (int i=1;i<1+l;i++) { for (int c=0;c<256;c++) if (F.transitionTable[i-1][c]!=-1) tt[i][c]=F.transitionTable[i-1][c]+1; for (int s:F.epsilonTransitionTable[i-1]) et[i].add(s+1); } return new NDFAutomaton(tt,et); } return null;
  }
  private static java.util.Set<Integer> eps(NDFAutomaton n,java.util.Set<Integer> S) { java.util.Set<Integer> cl=new java.util.HashSet<>(S); java.util.Stack<Integer> st=new java.util.Stack<>(); st.addAll(S); while (!st.isEmpty()) { int q=st.pop(); for (int r:n.epsilonTransitionTable[q]) if (!cl.contains(r)) { cl.add(r); st.push(r); } } return cl; }
  private static DFAutomaton step3(NDFAutomaton n) {
    java.util.HashMap<java.util.Set<Integer>,Integer> map=new java.util.HashMap<>(); java.util.ArrayList<java.util.Set<Integer>> states=new java.util.ArrayList<>(); java.util.Queue<Integer> q=new java.util.LinkedList<>(); java.util.Set<Integer> s0=eps(n,java.util.Collections.singleton(0)); map.put(s0,0); states.add(s0); q.add(0); java.util.ArrayList<java.util.HashMap<Integer,Integer>> trans=new java.util.ArrayList<>(); trans.add(new java.util.HashMap<>()); int fin=n.transitionTable.length-1;
    while (!q.isEmpty()) { int cur=q.poll(); java.util.Set<Integer> nfa=states.get(cur); for (int c=0;c<256;c++) { java.util.Set<Integer> next=new java.util.HashSet<>(); for (int s:nfa) if (n.transitionTable[s][c]!=-1) next.add(n.transitionTable[s][c]); if (next.isEmpty()) continue; java.util.Set<Integer> cl=eps(n,next); if (!map.containsKey(cl)) { int id=states.size(); map.put(cl,id); states.add(cl); q.add(id); trans.add(new java.util.HashMap<>()); } trans.get(cur).put(c,map.get(cl)); } }
    int[][] tt=new int[states.size()][256]; boolean[] acc=new boolean[states.size()]; for (int i=0;i<states.size();i++) { for (int c=0;c<256;c++) tt[i][c]=trans.get(i).getOrDefault(c,-1); acc[i]=states.get(i).contains(fin); } return new DFAutomaton(tt,acc);
  }
  private static DFAutomaton step4(DFAutomaton d) {
    int n=d.transitionTable.length; java.util.List<java.util.Set<Integer>> part=new java.util.ArrayList<>(); java.util.Set<Integer> acc=new java.util.HashSet<>(),nacc=new java.util.HashSet<>(); for (int i=0;i<n;i++) if (d.acceptingStates[i]) acc.add(i); else nacc.add(i); if (!acc.isEmpty()) part.add(acc); if (!nacc.isEmpty()) part.add(nacc);
    for (boolean ch=true;ch;) { ch=false; java.util.List<java.util.Set<Integer>> np=new java.util.ArrayList<>(); for (java.util.Set<Integer> block:part) { if (block.size()<=1) { np.add(block); continue; } java.util.Map<String,java.util.Set<Integer>> sp=new java.util.HashMap<>(); for (int s:block) { StringBuilder sb=new StringBuilder(); for (int c=0;c<256;c++) { int t=d.transitionTable[s][c]; int pi=-1; for (int i=0;i<part.size();i++) if (part.get(i).contains(t)) { pi=i; break; } sb.append(t==-1?"-1":pi).append(","); } String k=sb.toString(); sp.putIfAbsent(k,new java.util.HashSet<>()); sp.get(k).add(s); } if (sp.size()>1) ch=true; np.addAll(sp.values()); } part=np; }
    java.util.Map<Integer,Integer> toPart=new java.util.HashMap<>(); for (int i=0;i<part.size();i++) for (int s:part.get(i)) toPart.put(s,i); int[][] tt=new int[part.size()][256]; boolean[] acc2=new boolean[part.size()]; for (int i=0;i<part.size();i++) { int r=part.get(i).iterator().next(); acc2[i]=d.acceptingStates[r]; for (int c=0;c<256;c++) tt[i][c]=d.transitionTable[r][c]==-1?-1:toPart.get(d.transitionTable[r][c]); } int ip=toPart.get(0); if (ip!=0) { int[] T=tt[0]; tt[0]=tt[ip]; tt[ip]=T; boolean b=acc2[0]; acc2[0]=acc2[ip]; acc2[ip]=b; for (int i=0;i<tt.length;i++) for (int c=0;c<256;c++) { if (tt[i][c]==0) tt[i][c]=ip; else if (tt[i][c]==ip) tt[i][c]=0; } } return new DFAutomaton(tt,acc2);
  }
  private static void searchFile(DFAutomaton d,String path) throws java.io.IOException { java.io.BufferedReader r=new java.io.BufferedReader(new java.io.FileReader(path)); searchStream(d,r); r.close(); }
  private static void searchStream(DFAutomaton d,java.io.BufferedReader r) throws java.io.IOException { String line; while ((line=r.readLine())!=null) { if (d.acceptingStates[0]) { System.out.println(line); continue; } for (int i=0;i<line.length();i++) if (match(d,line,i)) { System.out.println(line); break; } } }
  private static boolean match(DFAutomaton d,String s,int start) { int st=0; for (int i=start;i<s.length();i++) { char c=s.charAt(i); if (c>=256) return false; int n=d.transitionTable[st][c]; if (n==-1) return false; st=n; if (d.acceptingStates[st]) return true; } return d.acceptingStates[st]; }
}
class RegExTree { int root; java.util.ArrayList<RegExTree> subTrees; RegExTree(int r,java.util.ArrayList<RegExTree> s) { root=r; subTrees=s; } }
class NDFAutomaton { int[][] transitionTable; java.util.ArrayList<Integer>[] epsilonTransitionTable; NDFAutomaton(int[][] t,java.util.ArrayList<Integer>[] e) { transitionTable=t; epsilonTransitionTable=e; } }
class DFAutomaton { int[][] transitionTable; boolean[] acceptingStates; DFAutomaton(int[][] t,boolean[] a) { transitionTable=t; acceptingStates=a; } }
