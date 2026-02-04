# Explication détaillée : Step1then2.java

## 1. But du programme

Ce programme fait **deux choses** à la suite :

1. **Étape 1** : il prend une **expression rationnelle** (regex) en texte (ex. `a.b`, `a*`, `a|b`) et la transforme en un **arbre de syntaxe**.
2. **Étape 2** : il transforme cet arbre en un **automate fini non déterministe** (NDFA) qui reconnaît exactement le langage décrit par la regex.

C’est l’algorithme classique du livre **Aho-Ullman** (compilation) : *regex → arbre → NDFA*.

---

## 2. Vue d’ensemble du flux

```
  Vous tapez une regex (ex. "a.b")
           ↓
  main() lit la regex (ligne de commande ou clavier)
           ↓
  parse()  →  construit un RegExTree (arbre)
           ↓
  step2_AhoUllman(ret)  →  construit un NDFAutomaton
           ↓
  Affichage de l’arbre et du NDFA
```

---

## 3. Les “constantes” (CONCAT, ETOILE, ALTERN, etc.)

```java
static final int CONCAT = 0xC04CA7;   // concaténation : "puis" (noté . dans l’arbre)
static final int ETOILE = 0xE7011E;  // étoile de Kleene : "répété 0 ou plus fois" (*)
static final int ALTERN = 0xA17E54;  // alternative : "ou" (|)
static final int PROTECTION = 0xBADDAD;  // marque pour les sous-expressions entre parenthèses
static final int PARENTHESEOUVRANT = 0x16641664;  // (
static final int PARENTHESEFERMANT = 0x51515151;  // )
static final int DOT = 0xD07;  // point = n’importe quel caractère (.)
```

Pourquoi des nombres au lieu des caractères `*`, `|`, `.` ?

- En **texte**, `.` peut être soit “concaténation”, soit “n’importe quel caractère”.
- En **arbre**, on a besoin de deux nœuds différents : un pour “concaténer” et un pour “n’importe quel caractère”. Donc on utilise des **codes uniques** (entiers) pour chaque type de nœud. Les valeurs en hexadécimal sont juste des identifiants pour ne pas se mélanger avec les codes ASCII des lettres.

---

## 4. La structure RegExTree (arbre de syntaxe)

```java
class RegExTree {
  protected int root;                    // type du nœud : CONCAT, ETOILE, 'a', etc.
  protected ArrayList<RegExTree> subTrees; // sous-arbres (enfants)
}
```

Exemples :

- **Feuille** (un caractère `a`) : `root = (int)'a'`, `subTrees` vide.
- **Étoile** (`a*`) : `root = ETOILE`, `subTrees` contient un seul enfant = l’arbre de `a`.
- **Concaténation** (`ab`) : `root = CONCAT`, `subTrees` = [arbre de `a`, arbre de `b`].
- **Alternative** (`a|b`) : `root = ALTERN`, `subTrees` = [arbre de `a`, arbre de `b`].

L’arbre représente donc la **structure** de la regex, pas juste la chaîne de caractères.

---

## 5. Étape 1 : de la regex (texte) à l’arbre

### 5.1 Entrée

La regex est une chaîne, par exemple `"a.b"`.

### 5.2 parse() — point d’entrée

```java
private static RegExTree parse() throws Exception {
  ArrayList<RegExTree> result = new ArrayList<RegExTree>();
  for (int i=0; i<regEx.length(); i++)
    result.add(new RegExTree(charToRoot(regEx.charAt(i)), new ArrayList<RegExTree>()));
  return parse(result);
}
```

- Chaque **caractère** de la regex est transformé en un **petit arbre d’un nœud** (feuille ou opérateur).
- `charToRoot(c)` donne le code du nœud : `'.'` → DOT, `'*'` → ETOILE, `'|'` → ALTERN, `'('` / `')'` → parenthèses, sinon le code ASCII du caractère.
- On obtient une **liste d’arbres** (une forêt), qu’on envoie à `parse(result)`.

### 5.3 parse(ArrayList<RegExTree> result) — priorité des opérateurs

L’ordre des `while` fixe la **priorité** (ce qui est traité en dernier est “en haut” dans l’arbre) :

```java
while (containParenthese(result)) result = processParenthese(result);  // d’abord les ()
while (containEtoile(result))    result = processEtoile(result);       // puis *
while (containConcat(result))    result = processConcat(result);       // puis concat
while (containAltern(result))    result = processAltern(result);      // enfin |
```

Donc : **parenthèses** > **étoile** > **concaténation** > **alternative**.

- **processParenthese** : trouve une `)`, remonte jusqu’à la `(`, prend ce qu’il y a entre les deux, appelle `parse()` sur ce morceau pour en faire un seul arbre, et remplace le tout par un nœud PROTECTION (pour garder ce bloc comme un sous-arbre).
- **processEtoile** : trouve un `*` “nu” (sans enfant), prend l’élément à sa gauche dans la liste, en fait son unique enfant, et remplace “élément + *” par un seul nœud ETOILE.
- **processConcat** : trouve deux éléments consécutifs (sans `|` entre eux), les regroupe en un nœud CONCAT avec ces deux sous-arbres, et remplace les deux par ce nœud. Répété jusqu’à ce qu’il n’y ait plus de concaténation à faire.
- **processAltern** : trouve un `|` “nu”, prend l’élément à gauche et l’élément à droite, crée un nœud ALTERN avec ces deux enfants, remplace les trois par ce nœud.

À la fin, `result` doit contenir **un seul** arbre ; sinon il y a une erreur de syntaxe.

### 5.4 removeProtection

Les nœuds PROTECTION servent uniquement à délimiter ce qui était entre parenthèses.  
`removeProtection` parcourt l’arbre et enlève ces nœuds en gardant uniquement leur enfant, pour obtenir l’arbre “nettoyé” qu’on va utiliser à l’étape 2.

---

## 6. Étape 2 : de l’arbre au NDFA (step2_AhoUllman)

Un **automate fini non déterministe** a :

- des **états** (numérotés 0, 1, 2, …) ;
- un **état initial** (ici toujours 0) ;
- un **état final** (ici toujours le dernier) ;
- des **transitions** :
  - soit en lisant un **caractère** (ex. `0 -- a --> 1`) ;
  - soit en **epsilon** (sans lire de caractère, ex. `0 -- epsilon --> 1`).

Le code représente ça par :

- **transitionTable[i][c]** = état atteint depuis l’état `i` en lisant le caractère `c` (-1 si pas de transition).
- **epsilonTransitionTable[i]** = liste des états atteignables depuis `i` par une transition epsilon.

La méthode **step2_AhoUllman(RegExTree ret)** construit ces tables **récursivement** selon le type du nœud `ret`.

### 6.1 Cas : feuille (un caractère ou “.”)

Si `ret.subTrees` est vide, c’est une feuille (un caractère ou le point).

- On crée **2 états** : 0 (initial) et 1 (final).
- Si ce n’est pas DOT : une transition `0 -- ret.root --> 1` (un seul caractère).
- Si c’est DOT : une transition pour **chaque** caractère (0..255) de 0 vers 1.

### 6.2 Cas : CONCAT (gauche puis droite)

- On construit le NDFA de l’enfant **gauche** et celui de l’enfant **droite**.
- On “colle” : l’état final du gauche devient relié à l’état initial du droite par une **transition epsilon**.
- On renumérote les états du droite et on copie toutes les transitions dans une grande table.  
Résultat : on traverse d’abord le gauche, puis par epsilon le droite.

### 6.3 Cas : ALTERN (gauche ou droite)

- On construit le NDFA du gauche et du droite.
- On ajoute un **nouvel état initial** et un **nouvel état final**.
- Depuis le nouvel initial : une epsilon vers l’ancien initial du gauche, une epsilon vers l’ancien initial du droite.
- Depuis chaque ancien état final (gauche et droite) : une epsilon vers le nouvel état final.  
Résultat : on choisit “en epsilon” d’aller dans le gauche ou dans le droite.

### 6.4 Cas : ETOILE (répétition 0 ou plus fois)

- On construit le NDFA du seul enfant.
- Nouvel état initial et nouvel état final.
- Epsilon : initial → ancien initial ; initial → final (pour 0 répétition).
- Epsilon : ancien final → nouvel final ; ancien final → ancien initial (pour recommencer).  
Résultat : on peut ne rien lire (0 fois) ou lire le sous-automate autant de fois qu’on veut.

---

## 7. La classe NDFAutomaton

Elle stocke simplement les deux tables :

- **transitionTable** : transitions par caractère.
- **epsilonTransitionTable** : transitions epsilon.

La méthode **toString()** affiche :

- l’état initial (0) et l’état final (dernier état) ;
- toutes les transitions epsilon ;
- toutes les transitions par caractère.

C’est exactement ce que le programme imprime après “BEGIN NDFA”.

---

## 8. Résumé en une phrase par partie

| Partie | Rôle |
|--------|------|
| **main** | Lit la regex, appelle parse() puis step2_AhoUllman(), affiche l’arbre et le NDFA. |
| **Constantes** | Identifier chaque type de nœud (concat, *, \|, ., parenthèses) dans l’arbre. |
| **RegExTree** | Arbre : un nœud = un type (opérateur ou caractère) + une liste de sous-arbres. |
| **parse()** | Texte → liste de petits arbres → enchaînement processParenthese / Etoile / Concat / Altern pour obtenir un seul arbre. |
| **processXxx** | Chacun regroupe des nœuds selon un opérateur (parenthèses, *, concat, \|). |
| **step2_AhoUllman** | Arbre → NDFA en construisant récursivement les tables de transition (feuille, CONCAT, ALTERN, ETOILE). |
| **NDFAutomaton** | Stocke et affiche les tables de transition de l’automate. |

Avec ça, vous pouvez suivre le code ligne par ligne en sachant à quoi sert chaque bloc.
