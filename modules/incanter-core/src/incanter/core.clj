;;; core.clj -- Core functions built on the CERN Colt Library

;; by David Edgar Liebke http://incanter.org
;; March 11, 2009

;; Copyright (c) David Edgar Liebke, 2009. All rights reserved.  The use
;; and distribution terms for this software are covered by the Eclipse
;; Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.htincanter.at the root of this
;; distribution.  By using this software in any fashion, you are
;; agreeing to be bound by the terms of this license.  You must not
;; remove this notice, or any other, from this software.

;; CHANGE LOG
;; March 11, 2009: First version

(ns ^{:doc "This is the core numerics library for Incanter.
            It provides functions for vector- and matrix-based
            mathematical operations and the core data manipulation
            functions for Incanter.

            This library is built on core.matrix (https://github.com/mikera/core.matrix)
            and Parallel Colt
            (http://sites.google.com/site/piotrwendykier/software/parallelcolt)
            an extension of the Colt numerics library
            (http://acs.lbl.gov/~hoschek/colt/).
            "
       :author "David Edgar Liebke"}

  incanter.core

  (:refer-clojure :exclude [abs update])
  (:use [incanter internal]
        [incanter.infix :only (infix-to-prefix defop)]
        [clojure.set :only (difference)]
        [clojure.pprint :only (print-table)])
  (:require [clojure.core.matrix :as m])
  (:require [clojure.core.matrix.dataset :as ds])
  (:require [clojure.core.matrix.linear :as l])
  (:import (clojure.core.matrix.impl.dataset DataSet)
           (cern.jet.math.tdouble DoubleArithmetic)
           (cern.jet.stat.tdouble Gamma)
           (javax.swing JTable JScrollPane JFrame)
           (java.util Vector)))

(set! *warn-on-reflection* false)

(def ^{:dynamic true
       :doc "This variable is bound to a dataset when the with-data macro is used.
              functions like $ and $where can use $data as a default argument."}
     $data nil)
(declare to-list to-vector vectorize dataset col-names to-matrix bind-rows)

(defn set-current-implementation [imp]
  "Sets current matrix implementation"
  (m/set-current-implementation imp))

(set-current-implementation :vectorz)

(defn matrix
"
  Returns a matrix or vector, in a valid core.matrix format. You can use the slices function to
  access the rows.

  Equivalent to R's matrix function.

  Examples:
    (def A (matrix [[1 2 3] [4 5 6] [7 8 9]])) ; produces a 3x3 matrix
    (def A2 (matrix [1 2 3 4 5 6 7 8 9] 3)) ; produces the same 3x3 matrix
    (def B (matrix [1 2 3 4 5 6 7 8 9])) ; produces a vector with 9 elements

    ; since (plus row1 row2) adds the two rows element-by-element
    (reduce plus A) ; produces the sums of the columns

    ; and since (sum row1) sums the elements of the row
    (map sum A) ; produces the sums of the rows

  "
  ([data]
     (m/matrix data))

  ([data ncol]
     (m/matrix (partition ncol (vectorize data))))

  ([init-val rows cols]
     (m/compute-matrix [rows cols] (constantly init-val))))

(defn matrix?
  "Tests if obj is core.matrix matrix"
  ([obj] (m/matrix? obj)))

(defn vec?
  "Tests if obj is core.matrix vector"
  ([obj] (m/vec? obj)))

(defn ^:deprecated dataset?
  "Determines if obj is of type clojure.core.matrix.impl.dataset.Dataset.

  Deprecated. Please use clojure.core.matrix.dataset/dataset? instead."
  ([obj] (ds/dataset? obj)))

(defn dispatch
  "Dispatch function for multimethods"
  ([obj]
     (cond
      (and (map? obj) (:charts obj)) ::multi-chart
      (dataset? obj) ::dataset
      (matrix? obj) ::matrix
      (vec? obj) ::vector
      (coll? obj) ::coll
      (.contains (str (type obj)) "processing.core.PApplet") :sketch
      :else (type obj))))

(defmulti nrow
  "Returns the number of rows in the given matrix. Equivalent to R's nrow function."
  dispatch)

(defmethod nrow ::dataset
  [ds] (m/row-count ds))

(defmethod nrow ::matrix
  [m] (m/row-count m))

(defmethod nrow ::vector
  [v] (m/row-count v))

(defmethod nrow ::coll
  [c] (count c))

(defmulti ncol
  "Returns the number of columns in the given matrix. Equivalent to R's ncol function."
  dispatch)

(defmethod ncol ::dataset
  [ds] (m/column-count ds))

(defmethod ncol ::matrix
  [m] (m/column-count m))

(defmethod ncol ::coll
  [c] 1)

(defmethod ncol ::vector
  [v] 1)

(defn ^:deprecated dim
  "Returns a vector with the number of rows and columns of the given matrix.

   Deprecated. Please use clojure.core.matrix/dimensionality instead.
  "
  ([mat]
     (m/shape mat)))

(defn ^:deprecated identity-matrix
  "
  Returns an n-by-n identity matrix.

  Examples:
  (identity-matrix 4)

  Deprecated. Please use clojure.core.matrix/identity-matrix instead.
  "
  ([^Integer n] (m/identity-matrix n)))


(defn ^:deprecated diag
  "If given a matrix, diag returns a sequence of its diagonal elements.
  If given a sequence, it returns a matrix with the sequence's elements
  on its diagonal. Equivalent to R's diag function.

  Examples:
  (diag [1 2 3 4]) ; produces diagonal matrix

  (def A (matrix [[1 2 3]
  [4 5 6]
  [7 8 9]]))
  (diag A) ;; returns elements on main diagonal

  Deprecated. Please use clojure.core.matrix/main-diagonal for getting elements on main diagonal
  and clojure.core.matrix/diagonal-matrix for creating diagonal matrix instead.
  "
  [m]
  (if (== 2 (m/dimensionality m))
    (matrix (m/main-diagonal m))
    (m/diagonal-matrix m)))


(defn ^:deprecated trans
  "
  Returns the transpose of the given matrix. Equivalent to R's t function

  Examples:
    (def A (matrix [[1 2 3]
                    [4 5 6]
                    [7 8 9]]))
    (trans A)

  Deprecated. Please use clojure.core.matrix/transpose instead.
  "
  ([mat]
   (m/transpose mat)))


(defn- except-for
  "
  Returns a lazy list of numbers ranging from 0 to n, except for the given exceptions.
  Examples:

    (except-for 10 3)
    (except-for 10 [5 7])
  "
  ([n exceptions]
    (let [except (if (coll? exceptions) exceptions [exceptions])]
      (for [i (range n) :when (reduce #(and %1 %2) (map #(not= i %) except))] i))))



(defmulti sel
  "
  Returns an element or subset of the given matrix, dataset, or list.
  If the column or row is specified as an atomic object (index or name), then
  the result will be returned as a list (only values from selected column or row).

  Argument:
    a matrix object, dataset, or list.

  Options:
    :rows (default true)
      returns all rows by default, can pass a row index or sequence of row indices
    :cols (default true)
      returns all columns by default, can pass a column index or sequence of column indices
    :except-rows (default nil) can pass a row index or sequence of row indices to exclude
    :except-cols (default nil) can pass a column index or sequence of column indices to exclude
    :filter-fn (default nil)
      a function can be provided to filter the rows of the matrix

  Examples:
    (use 'incanter.datasets)
    (def iris (to-matrix (get-dataset :iris)))
    (sel iris 0 0) ; first element
    (sel iris :rows 0 :cols 0) ; also first element
    (sel iris :cols 0) ; first column of all rows
    (sel iris :cols [0 2]) ; first and third column of all rows
    (sel iris :rows (range 10) :cols (range 2)) ; first two columns of the first 10 rows
    (sel iris :rows (range 10)) ; all columns of the first 10 rows

    ;; exclude rows or columns
    (sel iris :except-rows (range 10)) ; all columns of all but the first 10 rows
    (sel iris :except-cols 1) ; all columns except the second

    ;; return only the first 10 even rows
    (sel iris :rows (range 10) :filter-fn #(even? (int (nth % 0))))
    ;; select rows where distance (third column) is greater than 50
    (sel iris :filter #(> (nth % 2) 4))

    ;; examples with datasets
    (use 'incanter.datasets)
    (def us-arrests (get-dataset :us-arrests))
    (sel us-arrests :cols \"State\")
    (sel us-arrests :cols :State)

    (sel us-arrests :cols [\"State\" \"Murder\"])
    (sel us-arrests :cols [:State :Murder])
  "

  (fn [mat & options] [(dispatch mat) (keyword? (first options))]))


(defmethod sel [nil false] [])
(defmethod sel [nil true] [])

(defmethod sel [java.util.List false]
  ([^java.util.List lst rows cols]
    (sel lst :rows rows :cols cols)))

(defmethod sel [java.util.List true]
  ([^java.util.List lst & {:keys [rows cols except-rows except-cols filter-fn all]}]
    (let [rows (cond
                  rows rows
                  except-rows (except-for (nrow lst) except-rows)
                  :else true)
          cols (cond
                  cols cols
                  except-cols (except-for (nrow (first lst)) except-cols)
                  all all
                  :else true)
          lst (if (nil? filter-fn) lst (filter filter-fn lst))
          all-rows? (or (true? rows) (= rows :all) all)
          all-cols? (or (true? cols) (= cols :all) (= all :all))]
      (cond
        (and (number? rows) (number? cols))
          (nth (nth lst rows) cols)
        (and all-rows? (coll? cols))
          (map (fn [r] (map #(nth r %) cols)) lst)
        (and all-rows? (number? cols))
          (map #(nth % cols) lst)
        (and (coll? rows) (number? cols))
          (map #(nth % cols)
               (map #(nth lst %) rows))
        (and (coll? rows) all-cols?)
          (map #(nth lst %) rows)
        (and (number? rows) all-cols?)
          (nth lst rows)
        (and (number? rows) (coll? cols))
          (map #(nth (nth lst rows) %) cols)
        (and (coll? rows) (coll? cols))
          (map (fn [r] (map #(nth r %) cols))
               (map #(nth lst %) rows))
        (and all-rows? all-cols?)
          lst))))

(defmethod sel [::matrix false]
  ([mat rows columns]
     (matrix (m/select mat rows columns))))

(defmethod sel [::matrix true]
  ([mat & {:keys [rows cols except-rows except-cols filter-fn all]}]
   (let [rows (cond
                rows rows
                except-rows (except-for (m/row-count mat) except-rows)
                all all
                :else :all)
         cols (cond
                cols cols
                except-cols (except-for (m/column-count mat) except-cols)
                all all
                :else :all)
         mat (if (nil? filter-fn) mat (apply bind-rows (filter filter-fn mat)))]
     (matrix (m/select mat rows cols)))))

(prefer-method sel [::matrix true] [java.util.List true])
(prefer-method sel [::matrix false] [java.util.List false])

(defmethod sel :default
  ([mat & more]
    (apply sel (matrix mat) more)))

(defn bind-rows
  "
  Returns the matrix resulting from concatenating the given matrices
  and/or sequences by their rows. Equivalent to R's rbind.

  Examples:
  (def A (matrix [[1 2 3]
                  [4 5 6]
                  [7 8 9]]))

  (def B (matrix [[10 11 12]
                  [13 14 15]]))

  (bind-rows A B)

  (bind-rows [1 2 3 4] [5 6 7 8])
  "
  ([& args]
     (->>
      args
      (map
       #(let [dm (m/dimensionality %)]
          (case dm
            1 (m/row-matrix %)
            2 %
            (throw (RuntimeException.
                    (str "Can't bind rows to array of dimensionality " dm))))))
      (apply m/join))))

(defn bind-columns
  "
  Returns the matrix resulting from concatenating the given matrices
  and/or sequences by their columns. Equivalent to R's cbind.

  Examples:
  (def A (matrix [[1 2 3]
                  [4 5 6]
                  [7 8 9]]))

  (def B (matrix [10 11 12]))

  (bind-columns A B)

  (bind-columns [1 2 3 4] [5 6 7 8])
  "
  [& args]
  (->>
   args
   (map #(let [dm (m/dimensionality %)]
           (case dm
             1 (m/column-matrix %)
             2 %
             (throw (RuntimeException.
                     (str "Can't bind columns to array of dimensionality " dm))))))
   (apply m/join-along 1)))

(defn inner-product [& args]
  "Deprecated. Please use clojure.core.matrix/inner-product instead."
  (apply m/inner-product args))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; MATH FUNCTIONS
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn ^:deprecated plus
  "
  Performs element-by-element addition on multiple matrices, sequences
  and/or numbers. Equivalent to R's + operator.

  Examples:

  (def A (matrix [[1 2 3]
                  [4 5 6]
                  [7 8 9]]))
  (plus A A A)
  (plus A 2)
  (plus 2 A)
  (plus [1 2 3] [1 2 3])
  (plus [1 2 3] 2)
  (plus 2 [1 2 3])

  Deprecated. Please use clojure.core.matrix/add or
  clojure.core.matrix.operators/+ instead.
  "
  [& args] (apply m/add args))


(defn ^:deprecated minus
  "
  Performs element-by-element subtraction on multiple matrices, sequences
  and/or numbers. If only a single argument is provided, returns the negative
  of the given matrix, sequence, or number. Equivalent to R's - operator.

  Examples:
    (def A (matrix [[1 2 3]
                    [4 5 6]
                    [7 8 9]]))
    (minus A)
    (minus A A A)
    (minus A 2)
    (minus 2 A)
    (minus [1 2 3] [1 2 3])
    (minus [1 2 3] 2)
    (minus 2 [1 2 3])
    (minus [1 2 3])

  Deprecated. Please use clojure.core.matrix/sub or
  clojure.core.matrix.operators/- instead.
  "
  [& args] (apply m/sub args))

(defn ^:deprecated mult
  "
  Performs element-by-element multiplication on multiple matrices, sequences
  and/or numbers. Equivalent to R's * operator.

  Examples:

  (def A (matrix [[1 2 3]
                  [4 5 6]
                  [7 8 9]]))
  (mult A A A)
  (mult A 2)
  (mult 2 A)
  (mult [1 2 3] [1 2 3])
  (mult [1 2 3] 2)
  (mult 2 [1 2 3])

  Deprecated. Please use clojure.core.matrix/emul or
  clojure.core.matrix.operators/* instead.
  "
  [& args]
  (apply m/emul args))


(defn ^:deprecated div
  "
  Performs element-by-element division on multiple matrices, sequences
  and/or numbers. Equivalent to R's / operator.
  Examples:

  (def A (matrix [[1 2 3]
                  [4 5 6]
                  [7 8 9]]))
  (div A A A)
  (div A 2)
  (div 2 A)
  (div [1 2 3] [1 2 3])
  (div [1 2 3] 2)
  (div 2 [1 2 3])

  (div [1 2 3]) ; returns [1 1/2 13]

  Deprecated. Please use clojure.core.matrix/div or
  clojure.core.matrix.operators// instead.
  "
  ([& args] (apply m/div args)))


(defn safe-div
  "DivideByZero safe alternative to clojures / function,
  detects divide by zero and returns Infinity, -Infinity or NaN as appropriate.
  "
  ([x] (safe-div 1 x))
  ([x y]
     (m/emap
      #(try (m/div %1 %2)
            (catch ArithmeticException _
              (cond (> %1 0) Double/POSITIVE_INFINITY
                  (zero? %1) Double/NaN
                  :else Double/NEGATIVE_INFINITY)))
      x y))
  ([x y & more]
     (reduce safe-div (safe-div x y) more)))


(defn- mapping-helper [func args]
  (reduce (fn [A B]
            (cond
             (number? A) (func A B)
             (dataset? A) (dataset (col-names A)
                                   (mapping-helper func (list (m/rows A) B)))
              (or (matrix? A)
                  (m/vec? A)) (m/emap #(func %1 B) A)
              (and (coll? A) (coll? (first A)))
              (map (fn [a] (map #(func %1 B) a)) A)
              (coll? A) (map #(func %1 B) A)))
          args))

(defn pow  ;; TODO use jblas and fix meta
  "
  This is an element-by-element exponent function, raising the first argument
  by the exponents in the remaining arguments. Equivalent to R's ^ operator.
  "
  [& args]
  (mapping-helper #(Math/pow %1 %2) args))

(defn atan2 ;; TODO fix meta
  "
  Returns the atan2 of the elements in the given matrices, sequences or numbers.
  Equivalent to R's atan2 function.
  "
  [& args]
  (mapping-helper #(Math/atan2 %1 %2) args))

(defn sqrt
  "
  Returns the square-root of the elements in the given matrix, sequence or number.
  Equivalent to R's sqrt function.
  "
  [A] (m/sqrt A))


(defn sq
  "
  Returns the square of the elements in the given matrix, sequence or number.
  Equivalent to R's sq function.
  "
  ([A] (mult A A)))


(defn log
  "
  Returns the natural log of the elements in the given matrix, sequence or number.
  Equivalent to R's log function.
  "
  ([A] (m/log A)))


(defn log2
  "
  Returns the log base 2 of the elements in the given matrix, sequence or number.
  Equivalent to R's log2 function.
  "
  ([A] (transform-with A #(/ (Math/log %) (Math/log 2)))))


(defn log10
  "
  Returns the log base 10 of the elements in the given matrix, sequence or number.
  Equivalent to R's log10 function.
  "
  ([A] (m/log10 A)))


(defn exp
  "
  Returns the exponential of the elements in the given matrix, sequence or number.
  Equivalent to R's exp function."
  ([A] (m/exp A)))


(defn abs
  "
  Returns the absolute value of the elements in the given matrix, sequence or number.
  Equivalent to R's abs function.
  "
  ([A] (m/abs A)))


(defn sin
  "
  Returns the sine of the elements in the given matrix, sequence or number.
  Equivalent to R's sin function.
  "
  ([A] (m/sin A)))


(defn asin
  "
  Returns the arc sine of the elements in the given matrix, sequence or number.
  Equivalent to R's asin function.
  "
  ([A] (m/asin A)))


(defn cos
  "
  Returns the cosine of the elements in the given matrix, sequence or number.
  Equivalent to R's cos function.
  "
  ([A] (m/cos A)))


(defn acos
  "
  Returns the arc cosine of the elements in the given matrix, sequence or number.
  Equivalent to R's acos function."
  ([A] (m/acos A)))


(defn tan
  "
  Returns the tangent of the elements in the given matrix, sequence or number.
  Equivalent to R's tan function.
  "
  ([A] (m/tan A)))


(defn atan
  "
  Returns the arc tangent of the elements in the given matrix, sequence or number.
  Equivalent to R's atan function.
  "
  ([A] (m/atan A)))


(defn factorial
  "
  Returns the factorial of k (k must be a positive integer). Equivalent to R's
  factorial function.

  Examples:
    (factorial 6)

  References:
    http://incanter.org/docs/parallelcolt/api/cern/jet/math/tdouble/DoubleArithmetic.html
    http://en.wikipedia.org/wiki/Factorial

  "
  ([^Integer k] {:pre [(and (number? k) (not (neg? k)))]} (DoubleArithmetic/factorial k)))



(defn choose
  "
  Returns number of k-combinations (each of size k) from a set S with
  n elements (size n), which is the binomial coefficient (also known
  as the 'choose function') [wikipedia]
        choose = n!/(k!(n - k)!)

  Equivalent to R's choose function.

  Examples:
    (choose 25 6) ; => 177,100

  References:
    http://incanter.org/docs/parallelcolt/api/cern/jet/math/tdouble/DoubleArithmetic.html
    http://en.wikipedia.org/wiki/Combination
  "
  ([n k] (DoubleArithmetic/binomial (double n) (long k))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; MATRIX FUNCTIONS
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defmulti to-list
  "Returns a list-of-vectors if the given matrix is two-dimensional
   and a flat list if the matrix is one-dimensional."
  dispatch)

(defmethod to-list ::matrix
  ([mat]
     (cond
      (or (m/row-matrix? mat)
          (m/column-matrix? mat)
          (= (m/dimensionality mat) 1)) (apply list (m/to-vector mat))
      (m/scalar? mat) mat
      :default (apply list (map #(apply list %)
                                (m/to-nested-vectors mat))))))

(defmethod to-list ::dataset
  [data]
  (->> (m/rows data) (map #(apply list %)) (apply list)))

(defmethod to-list ::vector
  [data]
  (apply list data))

(defmethod to-list nil [s] nil)

(defmethod to-list :default [s] s)

(defn ^:deprecated copy
  "
  Deprecated. Please use clojure.core.matrix/clone instead.
  "
  ([mat]
     (m/clone mat)))

(defn to-vect
  "Converts an array into nested Clojure vectors. 

  Returns a vector-of-vectors if the given matrix is two-dimensional
  and a flat vector if the matrix is one-dimensional. This is a bit
  slower than the to-list function
  "
  [a]
  (m/to-nested-vectors a))

(defn ^:deprecated mmult
  "
  Returns the matrix resulting from the matrix multiplication of the
  the given arguments. Equivalent to R's %*% operator.

  Examples:

    (def A (matrix [[1 2 3]
                    [4 5 6]
                    [7 8 9]]))
    (mmult A (trans A))
    (mmult A (trans A) A)

  References:
    http://en.wikipedia.org/wiki/Matrix_multiplication

  Deprecated. Please use clojure.core.matrix/mmul instead.
  "
  ([& args]
     (apply m/mmul args)))


(defn kronecker
  "
  Returns the Kronecker product of the given arguments.

  Examples:

    (def x (matrix (range 6) 2))
    (def y (matrix (range 4) 2))
    (kronecker 4 x)
    (kronecker x 4)
    (kronecker x y)
  "
  ([& args]
     (reduce (fn [A B]
               (let [adims (long (m/dimensionality A))
                     bdims (long (m/dimensionality B))]
                 (cond
                  (and (== adims 0) (== bdims 0)) (* A B)
                  (and (== adims 1) (== bdims 1))
                  (-> (for [a B b B] (* a b))
                      (matrix))
                  (and (== adims 1) (== bdims 0)) (mult A B)
                  (and (== adims 2) (== bdims 2))
                  (apply bind-rows
                         (for [i (range (nrow A))]
                           (apply bind-columns
                                  (for [j (range (ncol A))]
                                    (mult (sel A i j) B)))))
                  (and (== adims 2) (== bdims 0)) (recur A (matrix [[B]]))
                  (and (== adims 2) (== bdims 1)) (recur A (m/column-matrix B)))))
            args)))

(defn ^:deprecated solve
  "
  Returns a matrix solution if A is square, least squares solution otherwise.
  Equivalent to R's solve function.

  Examples:
    (solve (matrix [[2 0 0] [0 2 0] [0 0 2]]))

  References:
    http://en.wikipedia.org/wiki/Matrix_inverse

  Deprecated. Please use clojure.core.matrix/inverse for matrix inverse,
  clojure.core.matrix.linear/solve for solving system of linear equations and
  clojure.core.matrix.linear/least-squares for least-squares solution.

  "
([A B]
   (l/solve A B))
([A]
   (l/solve A)))

(defn ^:deprecated det
  "
  Returns the determinant of the given matrix. Equivalent
  to R's det function.

   References:
    http://en.wikipedia.org/wiki/LU_decomposition

  Deprecated. Please use clojure.core.matrix/det instead.
  "
  ([mat]
     (m/det mat)))


(defn ^:deprecated trace
  "
  Returns the trace of the given matrix.

  References:
  http://en.wikipedia.org/wiki/Matrix_trace

  Deprecated. Please use clojure.core.matrix/trace instead.
  "
  [mat]
  (m/trace mat))


(defn vectorize
  "
  Returns the vectorization (i.e. vec) of the given matrix.
  The vectorization of an m-by-n matrix A, denoted by vec(A)
  is the m*n-by-1 column vector obtain by stacking the columns
  of the matrix A on top of one another.

  For instance:
    (= (vectorize (matrix [[a b] [c d]])) (matrix [a c b d]))

  Examples:
    (def A (matrix [[1 2] [3 4]]))
    (vectorize A)

  References:
    http://en.wikipedia.org/wiki/Vectorization_(mathematics)
  "
  ([mat]
     (m/to-vector mat)))

(defn half-vectorize
  "
  Returns the half-vectorization (i.e. vech) of the given matrix.
  The half-vectorization, vech(A), of a symmetric nxn matrix A
  is the n(n+1)/2 x 1 column vector obtained by vectorizing only
  the upper triangular part of A.

  For instance:
    (= (half-vectorize (matrix [[a b] [b d]])) (matrix [a b d]))

  Examples:
    (def A (matrix [[1 2] [2 4]]))
    (half-vectorize A)

  References:
    http://en.wikipedia.org/wiki/Vectorization_(mathematics)
  "
  ([mat]
   (for [j (range (nrow mat)) i (range j (nrow mat))] (sel mat i j))))



(defn ^:deprecated sum-of-squares
  "
  Returns the sum-of-squares of the given sequence.

  Deprecated. Please use clojure.core.matrix/length-squared instead.
  "
  ([x]
     (if (or (m/row-matrix? x)
             (m/column-matrix? x))
       (m/length-squared (m/to-vector x))
       (m/length-squared x))))


(defn ^:deprecated sum
  "
  Returns the sum of the given sequence.

  Deprecated. Please use clojure.core.matrix/esum instead.
  "
  ([x]
     (m/esum x)))


(defn prod
  "Returns the product of the given sequence."
  ([x]
     (m/ereduce *' x)))



(defn cumulative-sum
  "
  Returns a sequence of cumulative sum for the given collection. For instance
  The first value equals the first value of the argument, the second value is
  the sum of the first two arguments, the third is the sum of the first three
  arguments, etc.

  Examples:
    (use 'incanter.core)
    (cumulative-sum (range 100))
  "
  ([coll]
   (loop [in-coll (rest coll)
          cumu-sum [(first coll)]
          cumu-val (first coll)]
     (if (empty? in-coll)
       cumu-sum
       (let [cv (+ cumu-val (first in-coll))]
         (recur (rest in-coll) (conj cumu-sum cv) cv))))))





;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; MATRIX DECOMPOSITION FUNCTIONS
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn ^:deprecated decomp-cholesky
  "
  Returns the Cholesky decomposition of the given matrix. Equivalent to R's
  chol function.

  Returns:
  a map containing two matrices with the keys [:L :L*] such that

  Such that:
      M = L.L*

  Where
     - M must be a hermitian, positive definite matrix
     - L is a lower triangular matrix
     - L* is the conjugate transpose of L

  If :return parameter is specified in options map, it returns only specified keys.

  Examples:
  (use '(incanter core stats charts datasets))
  ;; load the iris dataset
  (def iris (to-matrix (get-dataset :iris)))
  ;; take the Cholesky decomposition of the correlation matrix of the iris data.
  (let [{:keys [L L*]} (decomp-cholesky (correlation iris))])
  (let [{:keys [L*]} (decomp-cholesky (correlation iris {:return [:L*]}))])

  References:
    http://en.wikipedia.org/wiki/Cholesky_decomposition

  Deprecated. Please use clojure.core.matrix.linear/cholesky instead.
  "
  ([mat] (l/cholesky mat))
  ([mat options] (l/cholesky mat options)))


(defn ^:deprecated decomp-svd
  "
  Returns the Singular Value Decomposition (SVD) of the given matrix. Equivalent to
  R's svd function.

  If :return parameter is specified in options map, it returns only specified keys.
  By default returns a map containing:
  :S -- the diagonal matrix of singular values S (the diagonal in vector form)
  :U -- the left singular vectors U
  :V* -- the right singular vectors V

  Examples:

  (use 'incanter.core)
  (def foo (matrix (range 9) 3))
  (let [{:keys [U S V*]} (decomp-svd foo)] ....)
  (let [{:keys [S]} (decomp-svd foo {:return [:S]})] ....)

  References:
  http://en.wikipedia.org/wiki/Singular_value_decomposition

  Deprecated. Please use clojure.core.matrix.linear/svd instead.
  "
  ([mat]
     (l/svd  mat))
  ([mat options]
     (l/svd mat options)))

(defn ^:deprecated decomp-eigenvalue
  "
  Returns a map containing matrices for each of the the keys [:Q :rA :iA] such that:

      M = Q.A.Q-1

   Where:
     - Q is a matrix where each column is the ith normalised eigenvector of M
     - rA is a vector whose elements are the real numbers of eigenvalues.
     - iA is a vector whose elements are the imaginary units of eigenvalues.
     - Q⁻-1 is the inverse of Q

   If :return parameter is specified in options map, it returns only specified keys.
   if :symmetric parameter is true in options map, symmetric eigenvalue decomposition will be performed.

  Examples:

  (use 'incanter.core)
  (def foo (matrix (range 9) 3))
  (let [{:keys [Q rA iA]} (decomp-eigenvalue M)])
  (let [{:keys [Q rA iA]} (decomp-eigenvalue M {:symmetric true})])
  (let [{:keys [Q rA]} (decomp-eigenvalue M {:return [:Q :rA]})])

  References:
  http://en.wikipedia.org/wiki/Eigenvalue_decomposition

  Deprecated. Please use clojure.core.matrix.linear/eigen instead.
  "
  ([mat] (l/eigen mat))
  ([mat options] (l/eigen mat options)))


(defn ^:deprecated decomp-lu
  "
  Computes the LU(P) decomposition of a matrix with partial row pivoting.
  Returns a map containing the keys [:L :U :P], such that:

       P.A = L.U

   Where
     - L is a lower triangular matrix
     - U is an upper triangular matrix
     - P is a permutation matrix

  Examples:

  (use 'incanter.core)
  (def foo (matrix (range 9) 3))
  (let [{:keys [L U P]} (decomp-lu A)])

  References:
    http://en.wikipedia.org/wiki/LU_decomposition
    http://mikiobraun.github.io/jblas/javadoc/org/jblas/Decompose.LUDecomposition.html

  Deprecated. Please use clojure.core.matrix.linear/lu instead.
  "
  ([mat] (l/lu mat))
  ([mat options] (l/lu mat options)))

(defn ^:deprecated vector-length [u]
  "Deprecated. Please use clojure.core.matrix/length instead."
  (m/length u))

(defn ^:deprecated inner-product [u v]
  (m/inner-product u v))

(defn proj [u v]
  (mult (div (inner-product v u) (inner-product u u)) u))

(defn ^:deprecated decomp-qr
  "
  Returns the QR decomposition of the given matrix. Equivalent to R's qr function.
  Returns a map containing matrices with the keys [:Q :R] such that:

        M = Q.R

  Where:
        - Q is an orthogonal matrix
        - R is an upper triangular matrix (= right triangular matrix)

  If :return parameter is specified in options map, it returns only specified keys.
  If :compact parameter is specified in options map, compact versions of matrices are returned.

  Examples:

  (use 'incanter.core)
  (def foo (matrix (range 9) 3))
  (let [{:keys [Q R]} (qr M)])
  (let [{:keys [R]} (qr M {:return [:R]})])

  References:
  http://en.wikipedia.org/wiki/QR_decomposition

  Deprecated. Please use clojure.core.matrix.linear/qr instead.
  "
  ([mat] (l/qr mat))
  ([mat options] (l/qr mat options)))

(defn condition
  "
  Returns the two norm condition number, which is max(S) / min(S), where S is the diagonal matrix of singular values from an SVD decomposition.


  Examples:
    (use 'incanter.core)
    (def foo (matrix (range 9) 3))
    (condition foo)

  References:
    http://en.wikipedia.org/wiki/Condition_number
  "
  ([mat]
    (let [s (:S (decomp-svd mat))]
      (/ (m/emax s) (m/emin s)))))


(defn ^:deprecated rank
  "
  Returns the effective numerical matrix rank, which is the number of nonnegligible singular values.

  Examples:

  (use 'incanter.core)
  (def foo (matrix (range 9) 3))
  (rank foo)

  References:
  http://en.wikipedia.org/wiki/Matrix_rank

  Deprecated. Please use clojure.core.matrix.linear/rank instead.
  "
  [mat]
  (l/rank mat))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; MISC FUNCTIONS
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn ^:deprecated length
  "
  A version of count that works on collections, matrices, and numbers.
  The length of a number is one, the length of a collection is its count
  and the length of a matrix is the number of elements it contains (nrow*ncol).
  Equivalent to R's length function.

  Deprecated. Please use clojure.core.matrix/ecount instead.
  "
  ([coll]
   (m/ecount coll)))

(defn group-on
  "
  Groups the given matrix by the values in the columns indicated by the
  'on-cols' argument, returning a sequence of matrices. The returned
  matrices are sorted by the value of the group column ONLY when there
  is only a single (non-vector) on-col argument.

  Examples:

    (use '(incanter core datasets))
    (def plant-growth (to-matrix (get-dataset :plant-growth)))
    (group-on plant-growth 1)
    ;; only return the first column
    (group-on plant-growth 1 :cols 0)
    ;; don't return the second column
    (group-on plant-growth 1 :except-cols 1)

    (def plant-growth-dummies (to-matrix (get-dataset :plant-growth) :dummies true))
    (group-on plant-growth-dummies [1 2])
    ;; return only the first column
    (group-on plant-growth-dummies [1 2] :cols 0)
    ;; don't return the last two columns
    (group-on plant-growth-dummies [1 2] :except-cols [1 2])

    ;; plot the plant groups
    (use 'incanter.charts)
    ;; can use destructuring if you know the number of groups
    ;; groups are sorted only if the group is based on a single column value
    (let [[ctrl trt1 trt2] (group-on plant-growth 1 :cols 0)]
      (doto (box-plot ctrl)
            (add-box-plot trt1)
            (add-box-plot trt2)
            view))
  "
  ([mat on-cols & {:keys [cols except-cols]}]
    (let [groups (if (coll? on-cols)
                   (into #{} (to-list (sel mat :cols on-cols)))
                   (sort (into #{} (to-list (sel mat :cols on-cols)))))
          filter-fn (fn [group]
                      (cond
                        (and (coll? on-cols) (> (count on-cols) 1))
                          (fn [row]
                            (reduce #(and %1 %2)
                                    (map (fn [i g] (= (m/mget row i) g)) on-cols group)))
                        (and (coll? on-cols) (= (count on-cols) 1))
                          (fn [row]
                            (= (m/mget row (first on-cols)) group))
                        :else
                          (fn [row]
                            (= (m/mget row on-cols) group))))
         ]
      (cond
        cols
          (map #(sel mat :cols cols :filter-fn (filter-fn %)) groups)
        except-cols
          (map #(sel mat :except-cols except-cols :filter-fn (filter-fn %)) groups)
        :else
          (map #(sel mat :filter-fn (filter-fn %)) groups)))))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; DATASET FUNCTIONS
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn dataset
  "Returns a record of type clojure.core.matrix.impl.dataset.DataSet.
   Creates dataset from:
    column names and seq of rows
    column names and seq of row maps
    map of columns with associated list of values.
    matrix - its columns will be used as dataset columns and incrementing Long values starting from 0, i.e. 0, 1, 2, etc will be used as column names.
    seq of maps

  Deprecated. Please use clojure.core.matrix.dataset/dataset instead.
  "
  ([m]
     (ds/dataset m))
  ([column-names m]
     (ds/dataset column-names m)))

(defn- get-column-id [dataset column-key]
  (let [headers (ds/column-names dataset)
        error "Column not found."]
    (cond
     (some #{column-key} headers)
     column-key

     (and (number? column-key) ;; if the given column name is a number
          ;; and this number is not in headers
          (not (some #(= column-key %) headers)))
     (nth headers column-key) ;; get nth column from headers

     (and (keyword? column-key) ;; if the given column name is a keyword, and
          ;; a keyword column name wasn't used in the dataset, and
          (not (some #{column-key} headers))
          ;; a string version was used in the dataset
          (some #{(name column-key)} headers))
     (throw (RuntimeException. (str error " Typo? There is a column with the same name of string type.")))

     (and (string? column-key) ;; if the given column is a string, and
          ;; this column was't used in the dataset, and
          (not (some #{column-key} headers))
          ;; a keyword column name was used in the dataset
          (some #{(keyword column-key)} headers))
     (throw (RuntimeException. (str error " Typo? There is a column with the same name of keyword type.")))

     :else (throw (RuntimeException. error)))))

(defn- map-get
  ([m k]
    (if (keyword? k)
      (or (get m k) (get m (name k)))
      (get m k)))
  ([m k colnames]
    (cond
      (keyword? k)
        (or (get m k) (get m (name k)))
      (number? k)
        (get m (nth colnames k))
      :else
        (get m k))))

(defn- submap [m ks]
  (zipmap (if (coll? ks) ks [ks])
          (map #(map-get m %) (if (coll? ks) ks [ks]))))




(defn query-to-pred
  "
  Given a query-map, it returns a function that accepts a hash-map and returns true if it
  satisfies the conditions specified in the provided query-map.

  Examples:

    (use 'incanter.core)
    (def pred (query-to-pred {:x 5 :y 7}))
    (pred {:x 5 :y 7 :z :d})

    (def pred (query-to-pred {:x 5 :y {:$gt 5 :$lt 10}}))
    (pred {:x 5 :y 7 :z :d})

    (def pred (query-to-pred {:z {:$in #{:a :b}}}))
    (pred {:x 5 :y 7 :z :d})
  "
  ([query-map]
    (let [in-fn (fn [value val-set] (some val-set [value]))
          nin-fn (complement in-fn)
          ops {:gt #(> (compare %1 %2) 0)
               :lt #(< (compare %1 %2) 0)
               :eq =
               :ne not=
               :gte #(>= (compare %1 %2) 0)
               :lte #(<= (compare %1 %2) 0)
               :in in-fn :nin nin-fn :fn (fn [v f] (f v))
               :$gt #(> (compare %1 %2) 0)
               :$lt #(< (compare %1 %2) 0)
               :$eq = :$ne not=
               :$gte #(>= (compare %1 %2) 0)
               :$lte #(<= (compare %1 %2) 0)
               :$in in-fn :$nin nin-fn
               :$fn (fn [v f] (f v))}
          _and (fn [a b] (and a b))]
      (fn [row]
        (reduce _and
                (for [k (keys query-map)]
                  (if (map? (query-map k))
                    (reduce _and
                            (for [sk (keys (query-map k))]
                              (cond
                               (fn? sk)
                                 (sk (row k) ((query-map k) sk))
                               (nil? (ops sk))
                                 (throw (Exception. (str "Invalid key in query-map: " sk)))
                               :else
                                ((ops sk) (row k) ((query-map k) sk)))))
                    (= (row k) (query-map k)))))))))


(defn query-dataset
  "
  Queries the given dataset using the query-map, returning a new dataset.
  The query-map uses the the dataset's column-names as keys and a
  simple variant of the MongoDB query language.

  For instance, given a dataset with two columns, :x and :category,  to query
  for rows where :x equals 10, use the following query-map: {:x 10}.

  To indicate that :x should be between 10 and 20, use {:x {:$gt 10 :$lt 20}}.

  To indicate that :category should also be either :red, :green, or :blue, use :$in
  {:x {:$gt 10 :$lt 20} :y {:$in #{:green :blue :red}}}

  And to indicate that :category should not include :red, :green, or :blue, use :$nin
  {:x {:$gt 10 :$lt 20} :y {:$nin #{:green :blue :red}}}

  The available query terms include :$gt, :$lt, :$gte, :$lte, :$eq, :$ne, :$in, :$nin, $fn.

  A row predicate function can be used instead of a query-map. The function must accept
  a map, representing a row of the dataset, and return a boolean value indicating whether
  the row should be included in the new dataset.

  Examples:
    (use '(incanter core datasets))
    (def cars (get-dataset :cars))

    (view (query-dataset cars {:speed 10}))
    (view (query-dataset cars {:speed {:$in #{17 14 19}}}))
    (view (query-dataset cars {:speed {:$lt 20 :$gt 10}}))
    (view (query-dataset cars {:speed {:$fn #(> (log %) 3)}}))

    ;; use a row predicate function instead of a query map.
    (view (query-dataset cars (fn [row] (> (/ (row \"speed\") (row \"dist\")) 1/2))))
     (assoc data :rows
             (for [row (:rows data) :when (query-map row)] row))
  "
  ([data query-map]
     (if (fn? query-map)
       (->> (ds/row-maps data)
            (filter query-map)
            (dataset (ds/column-names data)))
       (query-dataset data (query-to-pred query-map)))))



(defn- except-for-cols
  ([data except-cols]
     (let [colnames (ds/column-names data)
           _except-cols (if (coll? except-cols)
                          (map #(get-column-id data %) except-cols)
                          [(get-column-id data except-cols)])
           except-names  (if (some number? _except-cols)
                           (map #(nth colnames %) _except-cols)
                           _except-cols)]
       (for [name colnames :when (not (some #{name} except-names))]
         name))))


(defmethod sel [::dataset true]
  ([data & {:keys [rows cols except-rows except-cols filter-fn all]}]
     (let [except-cols (cond
                        (nil? except-cols) except-cols
                        (coll? except-cols) except-cols
                        :else [except-cols])
           rows (cond
                 rows rows
                 except-rows (except-for (nrow data) except-rows)
                 :else :all)
           cols (cond
                 cols cols
                 except-cols (ds/column-names (ds/remove-columns data except-cols))
                 all all
                 :else :all)
           col-names (ds/column-names data)
           col-set (into #{} col-names)
           r (cond
              (= rows :all) (range (m/row-count data))
              (number? rows) [rows]
              :else rows)
           c (cond
              (= cols :all) (ds/column-names data)

              (and (number? cols)
                   (not (contains? col-set cols)))
              [(nth col-names cols)]

              (and (coll? cols)
                   (every? number? cols)
                   (not (every? #(contains? col-set %) cols)))
              (map #(nth col-names %) cols)

              (not (coll? cols)) [cols]

              :else cols)
           res (-> (ds/select-rows data r)
                   (ds/select-columns c))
           res (if-not (nil? filter-fn)
                 (->> (ds/row-maps res) 
                      (mapv #(mapv % col-names))
                      (clojure.core/filter filter-fn))
                 res)]

       (cond
        (and (= (count c) 1) (not (or (coll? cols) (= cols :all))))
        (if (= (m/row-count res) 1)
          (m/mget res 0 0)
          (mapcat identity (m/rows res)))

        (and (= (m/row-count res) 1)
             (not (or (coll? rows) (= rows :all))))
        (into [] (m/get-row res 0))
        :else res))))

(defn fill-missing [maps]
  (let [ks (into #{} (mapcat keys maps))
        diff-m (zipmap ks (repeat nil))]
    (reduce
     (fn [acc m]
       (conj acc (merge diff-m m)))
     [] maps)))

(defn to-dataset
  "
  Returns a dataset containing the given values.

  Examples:

    (use 'incanter.core)
    (to-dataset 1)
    (to-dataset :a)
    (to-dataset [:a])
    (to-dataset (range 10))
    (to-dataset (range 10) :transpose true)
    (to-dataset [[1 2] [3 4] [5 6]])
    (to-dataset {:a 1 :b 2 :c 3})
    (to-dataset {\"a\" 1 \"b\" 2 \"c\" 3})
    (to-dataset [{:a 1 :b 2} {:a 1 :b 2}])
    (to-dataset [{\"a\" 1 \"b\" 2 \"c\" 3} {\"a\" 1 \"b\" 2 \"c\" 3}])
    "
  ([obj & {:keys [transpose]}]
     (let [obj (cond
                (map? obj) obj
                (dataset? obj) obj
                (= (m/dimensionality obj) 0) [[obj]]
                (and (= (m/dimensionality obj) 1) (map? (first obj))) (fill-missing obj)
                (= (m/dimensionality obj) 1) (mapv (fn [k] [k]) obj)
                :else obj)]
       (if transpose
         (dataset (m/transpose obj))
         (dataset obj)))))




(defn ^:deprecated col-names
  "
  If given a dataset, it returns its column names. If given a dataset and a sequence
  of column names, it returns a dataset with the given column names.

  Examples:
    (use '(incanter core datasets))
    (def data (get-dataset :cars))
    (col-names data)

  Deprecated. Please use clojure.core.matrix.dataset/column-names instead.
  "
  ([data] (ds/column-names data)))



(defn conj-cols
  "
  Returns a dataset created by merging the given datasets and/or collections.
  There must be the same number of rows in each dataset and/or
  collections.  Column names may be changed in order to prevent
  naming conflicts in the conjed dataset.

  Examples:
    (use '(incanter core datasets))
    (def cars (get-dataset :cars))
    (def x (sel cars :cols 0))
    (view (conj-cols cars cars))
    (view (conj-cols cars x))
    (view (conj-cols (range (nrow cars)) cars))
    (view (conj-cols (range 10) (range 10)))
    (view (conj-cols {:a 1 :b 2} {:c 1 :d 2}))
  "
  ([& args]
     (if (= (count args) 1)
       (first args)
       (let [all-colnames (->> (filter #(ds/dataset? %) args)
                               (mapcat #(m/columns %))
                               (into #{}))
             name-f (filter #(not (contains? all-colnames %)) (range))
             args (loop [x (first args)
                         xs (next args)
                         i 0
                         acc []]
                    (if (nil? x)
                      acc
                      (recur
                       (first xs)
                       (next xs)
                       (+ i (ncol x))
                       (conj acc
                             (case (dispatch x)
                               ::dataset x
                               ::matrix (-> (take (ncol x) (drop i name-f))
                                            (dataset x))
                               (dataset (-> (take 1 (drop i name-f))
                                            (first)
                                            (hash-map x))))))))]

         (apply ds/merge-datasets args)))))


(defn ^:deprecated conj-rows
  "
  Returns a dataset created by combining the rows of the given datasets and/or collections.

  Examples:

    (use '(incanter core datasets))
    (def cars (get-dataset :cars))
    (view (conj-rows (to-dataset (range 5)) (to-dataset (range 5 10))))
    (view (conj-rows cars cars))
    (view (conj-rows [[1 2] [3 4]] [[5 6] [7 8]]))
    (view (conj-rows [{:a 1 :b 2} {:a 3 :b 4}] [[5 6] [7 8]]))
    (view (conj-rows (to-dataset [{:a 1 :b 2} {:a 3 :b 4}]) [[5 6] [7 8]]))
    (conj-rows (range 5) (range 5 10))

  Deprecated. Please use clojure.core.matrix/conj-rows instead.
  "
  ([& args]
     (let [args (map
                 (fn [x]
                   (if (dataset? x) x
                       (dataset x))) args)]
       (apply ds/join-rows args))))


(defn $
  "
  An alias to (sel (second args) :cols (first args)). If given only a single argument,
  it will use the $data binding for the first argument, which is set with
  the with-data macro.

  Examples:
    (use '(incanter core stats charts datasets))

    (def cars (get-dataset :cars))
    ($ :speed cars)


    (with-data cars
      (def lm (linear-model ($ :dist) ($ :speed)))
      (doto (scatter-plot ($ :speed) ($ :dist))
        view
        (add-lines ($ :speed) (:fitted lm))))

    ;; standardize speed and dist and append the standardized variables to the original dataset
    (with-data (get-dataset :cars)
      (view (conj-cols $data
                       (sweep (sweep ($ :speed)) :stat sd :fun div)
                       (sweep (sweep ($ :dist)) :stat sd :fun div))))

    (with-data (get-dataset :iris)
      (view $data)
      (view ($ [:Sepal.Length :Sepal.Width :Species]))
      (view ($ [:not :Petal.Width :Petal.Length]))
      (view ($ 0 [:not :Petal.Width :Petal.Length])))


     (use 'incanter.core)
     (def mat (matrix (range 9) 3))
     (view mat)
     ($ 2 2 mat)
     ($ [0 2] 2 mat)
     ($ :all 1 mat)
     ($ 1 mat)
     ($ [:not 1] mat)
     ($ 0 :all mat)
     ($ [0 2] [0 2] mat)
     ($ [:not 1] [:not 1] mat)
     ($ [:not 1] :all mat)
     ($ [0 2] [:not 1] mat)
     ($ [0 2] [:not 1 2] mat)
     ($ [0 2] [:not (range 2)] mat)
     ($ [:not (range 2)] [0 2] mat)

     (with-data mat
       ($ 0 0))
     (with-data mat
       ($ [0 2] 2 mat))
     (with-data mat
       ($ :all 1))
     (with-data mat
       ($ [0 2] [0 2]))
     (with-data mat
       ($ [:not 1] :all))
     (with-data mat
       ($ [0 2] [:not 1]))


     (use 'incanter.datasets)
     (view (get-dataset :cars))
     ($ (range 5) 0 (get-dataset :cars))
     ($ (range 5) :all (get-dataset :cars))
     ($ :all (range 2) (get-dataset :cars))

     ($ (range 5) :dist (get-dataset :cars))
     ($ [:not (range 5)] 0 (get-dataset :cars))
     ($ [:not 0 1 2 3 4] 0 (get-dataset :cars))
     (with-data (get-dataset :cars)
       ($ 0 :dist))

     (with-data (get-dataset :hair-eye-color)
       (view $data)
       (view ($ [:not :gender])))
  "

  ([cols]
    ($ :all cols $data))
  ([arg1 arg2]
    (let [rows-cols-data
          (cond (nil? arg2) [:all arg1 $data]
                (or (matrix? arg2) (dataset? arg2)) [:all arg1 arg2]
                :else [arg1 arg2 $data])]
      (apply $ rows-cols-data)))
  ([rows cols data]
    (let [except-rows? (and (vector? rows) (= :not (first rows)))
          except-cols? (and (vector? cols) (= :not (first cols)))
          _rows (if except-rows?
                  (conj [:except-rows]
                        (if (coll? (second rows))
                          (second rows)
                          (rest rows)))
                  [:rows rows])
          _cols (if except-cols?
                  (if (coll? (second cols))
                    (conj [:except-cols] (second cols))
                    (conj [:except-cols] (rest cols)))
                  [:cols cols])
          args (concat _rows _cols)]
      (apply sel data args))))

(defn head
  "Returns the head of the dataset. 10 or full dataset by default."
  ([len mat]
    (cond
      (= len 0) ($ :none :all mat)
      (<= len (- (nrow mat))) (head 0 mat)
      (< len 0) (head (+ (nrow mat) len) mat)
      :else ($ (range (min len (nrow mat))) :all mat)))
  ([mat]
    (head 10 mat)))

(defn tail
  "Returns the tail of the dataset. 10 or full dataset by default."
  ([len mat]
    (cond
      (= len 0) ($ :none :all mat)
      (<= len (- (nrow mat))) (head 0 mat)
      (< len 0) (head (+ (nrow mat) len) mat)
      :else ($ (range (max 0 (- (nrow mat) len)) (nrow mat)) :all mat)))
  ([mat]
    (tail 10 mat)))

(defn $where
  "
  An alias to (query-dataset (second args) (first args)). If given only a single argument,
  it will use the $data binding for the first argument, which is set with
  the with-data macro.

  Examples:

    (use '(incanter core datasets))

    (def cars (get-dataset :cars))
    ($where {:speed 10} cars)

    ;; use the with-data macro and the one arg version of $where
    (with-data cars
      (view ($where {:speed {:$gt -10 :$lt 10}}))
      (view ($where {:dist {:$in #{10 12 16}}}))
      (view ($where {:dist {:$nin #{10 12 16}}})))

    ;; create a dataset where :speed greater than 10 or less than -10
    (with-data (get-dataset :cars)
      (view (-> ($where {:speed {:$gt 20}})
                      (conj-rows ($where {:speed {:$lt 10}})))))
  "
  ([query-map]
    (query-dataset $data  query-map))
  ([query-map data]
    (query-dataset data query-map)))



(defn $rollup
  "
  Returns a dataset that uses the given summary function (or function identifier keyword)
  to rollup the given column based on a set of group-by columns. The summary function
  should accept a single sequence of values and return a single summary value. Alternatively,
  you can provide a keyword identifier of a set of built-in functions including:

  :max -- the maximum value of the data in each group
  :min -- the minimum value of the data in each group
  :sum -- the sum of the data in each group
  :count -- the number of elements in each group
  :mean -- the mean of the data in each group


  Like the other '$' dataset functions, $rollup will use the dataset bound to $data
  (see the with-data macro) if a dataset is not provided as an argument.

  Examples:

    (use '(incanter core datasets))

    (def iris (get-dataset :iris))
    ($rollup :mean :Sepal.Length :Species iris)
    ($rollup :count :Sepal.Length :Species iris)
    ($rollup :max :Sepal.Length :Species iris)
    ($rollup :min :Sepal.Length :Species iris)

    ;; The following is an example using a custom function, but since all the
    ;; iris measurements are positive, the built-in mean function could have
    ;; been used instead.

    (use 'incanter.stats)
    ($rollup #(mean (abs %)) :Sepal.Width :Species iris)

    ($rollup sd :Sepal.Length :Species iris)
    ($rollup variance :Sepal.Length :Species iris)
    ($rollup median :Sepal.Length :Species iris)

    (def hair-eye-color (get-dataset :hair-eye-color))
    ($rollup :mean :count [:hair :eye] hair-eye-color)

    (use 'incanter.charts)
    (with-data ($rollup :mean :Sepal.Length :Species iris)
      (view (bar-chart :Species :Sepal.Length)))

     ;; the following examples use the built-in data set called hair-eye-color.

     (with-data ($rollup :mean :count [:hair :eye] hair-eye-color)
       (view (bar-chart :hair :count :group-by :eye :legend true)))

     (with-data (->>  (get-dataset :hair-eye-color)
                      ($where {:hair {:in #{\"brown\" \"blond\"}}})
                      ($rollup :sum :count [:hair :eye])
                      ($order :count :desc))
       (view $data)
       (view (bar-chart :hair :count :group-by :eye :legend true)))
  "
  ([summary-fun col-name group-by]
    ($rollup summary-fun col-name group-by $data))
  ([summary-fun col-name group-by data]
    (let [key-fn (if (coll? col-name)
                   (fn [row]
                     (into [] (map #(map-get row %) col-name)))
                   (fn [row]
                     (map-get row col-name)))
          rows (ds/row-maps data)
          rollup-fns {:max (fn [col-data] (apply max col-data))
                      :min (fn [col-data] (apply min col-data))
                      :sum (fn [col-data] (apply + col-data))
                      :count count
                      :mean (fn [col-data] (/ (apply + col-data) (count col-data)))}
          rollup-fn (if (keyword? summary-fun)
                      (rollup-fns summary-fun)
                      summary-fun)]
      (loop [cur rows reduced-rows {}]
        (if (empty? cur)
          (let [group-cols (to-dataset (keys reduced-rows))
                res (conj-cols group-cols (map rollup-fn (vals reduced-rows)))
                new-col-names (concat (col-names group-cols)
                                      (if (coll? col-name) col-name [col-name]))]
            (ds/rename-columns res (zipmap (col-names res)
                                           new-col-names)))
          (recur (next cur)
                 (let [row (first cur)
                       k (submap row group-by)
                       a (reduced-rows k)
                       b (key-fn row)]
                   (assoc reduced-rows k (if a (conj a b) [b])))))))))


(defn $order
  "
  Sorts a dataset by the given columns in either ascending (:asc)
  or descending (:desc) order. If used within a the body of
  the with-data macro, the data argument is optional, defaulting
  to the dataset bound to the variable $data.

  Examples:

    (use '(incanter core charts datasets))
    (def iris (get-datset :iris))
    (view ($order :Sepal.Length :asc iris))
    (view ($order [:Sepal.Width :Sepal.Length] :desc iris))

    (with-data (get-dataset :iris)
      (view ($order [:Petal.Length :Sepal.Length] :desc)))

  "
  ([cols order]
    ($order cols order $data))
  ([cols order data]
    (let [key-cols (if (coll? cols) cols [cols])
          key-fn (fn [row] (into [] (map #(map-get row %) key-cols)))
          comp-fn (if (= order :desc)
                    (comparator (fn [a b] (pos? (compare a b))))
                    compare)]
      (ds/dataset (ds/column-names data)
                  (sort-by key-fn comp-fn (ds/row-maps data))))))



(defmacro $fn
  "
  A simple macro used as syntactic sugar for defining predicate functions to be used
  in the $where function. The supplied arguments should be column names of a dataset.
  This macro performs map destructuring on the arguments.

  For instance,
  ($fn [speed] (< speed 10)) => (fn [{:keys [speed]}] (< speed 10))

  Examples:
    (use '(incanter core datasets))
    (view ($where ($fn [speed dist] (or (> speed 20) (< dist 10))) (get-dataset :cars)))

    (view ($where ($fn [speed dist] (< (/ dist speed) 2)) (get-dataset :cars)))

    (use '(incanter core datasets charts))
    (with-data (get-dataset :cars)
      (doto (scatter-plot :speed :dist :data ($where ($fn [speed dist] (< (/ dist speed) 2))))
        (add-points :speed :dist :data ($where ($fn [speed dist] (>= (/ dist speed) 2))))
        (add-lines ($ :speed) (mult 2 ($ :speed)))
        view))


    (let [passed? ($fn [speed dist] (< (/ dist speed) 2))
          failed? (complement passed?)]
      (with-data (get-dataset :cars)
        (doto (scatter-plot :speed :dist :data ($where passed?))
          (add-points :speed :dist :data ($where failed?))
          (add-lines ($ :speed) (mult 2 ($ :speed)))
          view)))


    (use '(incanter core stats charts))
    (let [above-sine? ($fn [col-0 col-1] (> col-1 (sin col-0)))
          below-sine? (complement above-sine?)]
      (with-data (conj-cols (sample-uniform 1000 :min -5 :max 5)
                            (sample-uniform 1000 :min -1 :max 1))
        (doto (function-plot sin -5 5)
          (add-points :col-0 :col-1 :data ($where above-sine?))
          (add-points :col-0 :col-1 :data ($where below-sine?))
          view)))


    (view ($where ($fn [] (> (rand) 0.9)) (get-dataset :cars)))

    (view ($where ($fn [Species] ($in Species #{\"virginica\" \"setosa\"})) (get-dataset :iris)))
  "
  ([col-bindings body]
    `(fn [{:keys ~col-bindings}] ~body)))



(defn $group-by
  "
  Returns a map of datasets keyed by a query-map corresponding the group.

  Examples:

    (use '(incanter core datasets))
    ($group-by :Species (get-dataset :iris))

    ($group-by [:hair :eye] (get-dataset :hair-eye-color))

    (with-data (get-dataset :hair-eye-color)
      ($group-by [:hair :eye]))
  "
  ([cols]
    ($group-by cols $data))
  ([cols data]
     (let [cols (if (coll? cols)
                  cols
                  [cols])
           orig-col-names (ds/column-names data);save to preserve order below
           groups (group-by #(select-keys % cols) (ds/row-maps data))]
      (into {}
            (for [[group-value group-rows] groups]
              {group-value (ds/select-columns
                            (dataset group-rows)
                            orig-col-names)})))))


(defn ^:deprecated matrix-map
  "
  Like clojure.core/map, but will work on matrices of any dimension:
  1 x 1 (like e.g. a Double), 1 x n, n x 1, and n x m

  Examples:
    (use '(incanter core))
    (def mat (matrix (range 9) 3))
    (matrix-map #(mod % 2) mat)
    (matrix-map #(mod % 2) (first mat))
    (matrix-map #(mod % 2) ($ 1 0 mat))
    (matrix-map #(mod % 2) [1 2 3 4])
    (matrix-map #(mod % 2) 9)

  Deprecated. Please use clojure.core.matrix/emap instead.
  "
  ([f m]
     (m/emap f m))
  ([f m & ms]
     (apply m/emap f m ms)))


(defn $map
  "
  This function returns a sequence resulting from mapping the given function over
  the value(s) for the given column key(s) of the given dataset.
  Like other '$*' functions, it will use $data as the default dataset
  if none is provided, where $data is set using the with-data macro.

  Examples:

    (use '(incanter core datasets))
    (def cars (get-dataset :cars))

    ($map (fn [s] (/ s)) :speed cars)
    ($map (fn [s d] (/ s d)) [:speed :dist] cars)

    (with-data (get-dataset :cars)
      (view ($map (fn [s] (/ s)) :speed))
      (view ($map (fn [s d] (/ s d)) [:speed :dist])))

    ;; calculate the speed to dist ratio and append as new column to dataset
    (with-data (get-dataset :cars)
      (conj-cols $data ($map (fn [s d] (/ s d)) [:speed :dist])))
  "
  ([fun col-keys data]
     (let [rms (ds/row-maps data)]
      (if (coll? col-keys)
        (map (fn [row] (apply fun (map (fn [k] (map-get row k)) col-keys))) rms)
        (map (fn [row] (fun (map-get row col-keys))) rms))))
  ([fun col-keys]
    ($map fun col-keys $data)))



(defn $join
  "
  Returns a dataset created by right-joining two datasets.
  The join is based on one or more columns in the datasets.
  If used within the body of the with-data macro, the second
  dataset is optional, defaulting the the dataset bound to $data.


  Examples:
    (use '(incanter core stats datasets charts))
    (def iris (get-dataset :iris))



    (def lookup (dataset [:species :species-key] [[\"setosa\" :setosa]
                                                  [\"versicolor\" :versicolor]
                                                  [\"virginica\" :virginica]]))
    (view ($join [:species :Species] lookup iris))

    (def hair-eye-color (get-dataset :hair-eye-color))
    (def lookup2 (conj-cols ($ [:hair :eye :gender] hair-eye-color) (range (nrow hair-eye-color))))
    (view ($join [[:col-0 :col-1 :col-2] [:hair :eye :gender]] lookup2 hair-eye-color))

    (with-data hair-eye-color
      (view ($join [[:col-0 :col-1 :col-2] [:hair :eye :gender]] lookup2)))


    (def lookup3 (dataset [:gender :hair :hair-gender] [[\"male\" \"black\" :male-black]
                                                        [\"male\" \"brown\" :male-brown]
                                                        [\"male\" \"red\" :male-red]
                                                        [\"male\" \"blond\" :male-blond]
                                                        [\"female\" \"black\" :female-black]
                                                        [\"female\" \"brown\" :female-brown]
                                                        [\"female\" \"red\" :female-red]
                                                        [\"female\" \"blond\" :female-blond]]))

    (view ($join [[:gender :hair] [:gender :hair]] lookup3 hair-eye-color))

    (use 'incanter.charts)
    (with-data (->>  (get-dataset :hair-eye-color)
                     ($where {:hair {:in #{\"brown\" \"blond\"}}})
                     ($rollup :sum :count [:hair :gender])
                     ($join [[:gender :hair] [:gender :hair]] lookup3)
                     ($order :count :desc))
        (view $data)
        (view (bar-chart :hair :count :group-by :gender :legend true)))
  "
  ([[left-keys right-keys] left-data]
    ($join [left-keys right-keys] left-data $data))
  ([[left-keys right-keys] left-data right-data]
    (let [left-keys (if (coll? left-keys) left-keys [left-keys])
          right-keys (if (coll? right-keys) right-keys [right-keys])
          index (apply hash-map
                       (interleave
                        (map (fn [row]
                               (apply hash-map
                                      (interleave right-keys
                                                  (map #(map-get (submap row left-keys) %)
                                                       left-keys))))
                             (ds/row-maps left-data))
                        (map #(reduce dissoc % left-keys) (ds/row-maps left-data))))
          rows (map #(merge (index (submap % right-keys)) %) (ds/row-maps right-data))]
      (to-dataset rows))))

(defn aggregate
  "
  Performs the aggregation of the data in given dataset using the specified rollup function.
  The fields parameter defines column(s) on which the rollup will happen, and group-by
  specifies the column(s) for joining the results.  The fields & group-by parameters could be
  single values or collections.  The dataset is provided by the :dataset parameter, if it's not
  provided, then the $data is used.  The rollup function is provided by :rollup-fun parameter,
  if it's not provided, then the :sum is used.

    (aggregate [:uptake :conc] :Type :dataset (get-dataset :co2))
    (aggregate [:uptake :conc] [:Type] :dataset (get-dataset :co2) :rollup-fun :min)
"
  [fields group-by & {:keys [dataset rollup-fun] :or {rollup-fun :sum}}]
  (let [dset (or dataset $data)
        fields (if (coll? fields) fields [fields])
        group-by (if (coll? group-by) group-by [group-by])]
    (reduce #($join [group-by group-by] %1 %2)
            (map #($rollup rollup-fun % group-by dset)
                 fields))))

(defn- replace-by-number-or-value [col-vec [old-col new-col-name]]
  (if (number? old-col)
    (assoc col-vec old-col new-col-name)
    (replace {old-col new-col-name} col-vec)))

(defn rename-cols
  "
  Rename columns based on col-map of old-col new-col-name pairs.  If
  old-col is a number it is taken as a 0 based index for the column to
  replace

  Example:
   (use '(incanter core datasets))
   (rename-cols {:Sepal.Length :s.length 3 :p.width} (get-dataset :iris))
  "
  ([col-map]
    (rename-cols col-map $data))
  ([col-map data]
     (let [col-names (ds/column-names data)
           col-set (into #{} col-names)
           col-map (reduce
                    (fn [acc [k v]]
                      (if (and (not (contains? col-set k))
                               (number? k))
                        (assoc acc (nth col-names k) v)
                        (assoc acc k v)))
                    {} col-map)]
       (ds/rename-columns data col-map))))

(defn replace-column
  "Replaces a column in a dataset with new values."
  ([column-name values]
    (replace-column column-name values $data))
  ([column-name values data]
     (ds/replace-column data column-name values)))

(defn add-column
  "Adds a column, with given values, to a dataset."
  ([column-name values]
    (add-column column-name values $data))
  ([column-name values data]
     (if (contains? (into #{} (ds/column-names data)) column-name)
       (ds/replace-column data column-name values)
       (ds/add-column data column-name values))))

(defn add-derived-column
  "
  This function adds a column to a dataset that is a function of
  existing columns. If no dataset is provided, $data (bound by the
  with-data macro) will be used. f should be a function of the
  from-columns, with arguments in that order.

  Examples:
    (use '(incanter core datasets))
    (def cars (get-dataset :cars))

    (add-derived-column :dist-over-speed [:dist :speed] (fn [d s] (/ d s)) cars)

    (with-data (get-dataset :cars)
      (view (add-derived-column :speed**-1 [:speed] #(/ 1.0 %))))"

  ([column-name from-columns f]
    (add-derived-column column-name from-columns f $data))
  ([column-name from-columns f data]
     (let [d (ds/select-columns data from-columns)
           cols (m/columns d)
           derived-col (apply map f cols)]
       (ds/add-column data column-name derived-col))))

;; credit to M.Brandmeyer
(defn ^:deprecated transform-col
  "
  Apply function f & args to the specified column of dataset and replace the column
  with the resulting new values.

  Deprecated. Please use `clojure.core.matrix.dataset/update-column` instead.
  "
  [dataset column f & args]
  (apply ds/emap-column dataset column f args))


(defn deshape
  "
  Returns a dataset where the columns identified by :merge are collapsed into
  two columns called :variable and :value. The values in these columns are grouped
  by the columns identified by :group-by.

  Examples:

    (use '(incanter core charts datasets))
    (with-data (->> (deshape :merge [:Ahmadinejad :Rezai :Karrubi :Mousavi]
                              :group-by :Region
                              :data (get-dataset :iran-election))
                    ($order :value :desc))
      (view $data)
      (view (bar-chart :variable :value :group-by :Region :legend true))

      (view (bar-chart :Region :value :group-by :variable
                       :legend true :vertical false))

      (view (bar-chart :Region :value :legend true :vertical false
                       :data ($order :value :desc ($rollup :sum :value :Region)))))



      (def data (to-dataset [{:subject \"John Smith\" :time 1 :age 33 :weight 90 :height 1.87}
                             {:subject \"Mary Smith\" :time 1 :height 1.54}]))
      (view data)
      (view (deshape :group-by [:subject :time] :merge [:age :weight :height] :data data))
      (view (deshape :merge [:age :weight :height] :data data))
      (view (deshape :group-by [:subject :time] :data data))

      (view (deshape :merge [:age :weight :height] :remove-na false :data data))
  "
  ([& {:keys [data remove-na group-by merge] :or {remove-na true}}]
    (let [data (or data $data)
          colnames (col-names data)
          _group-by (into #{} (when group-by
                                (if (coll? group-by)
                                  group-by
                                  [group-by])))
          _merge (into #{} (when merge
                               (if (coll? merge)
                                 merge
                                 [merge])))
          __group-by (if (empty? _group-by)
                       (difference (into #{} (col-names data)) _merge)
                       _group-by)
          __merge (if (empty? _merge)
                       (difference (into #{} (col-names data)) _group-by)
                       _merge)
          deshaped-data (mapcat (fn [row]
                                  (let [base-map (zipmap __group-by
                                                         (map #(map-get row % colnames) __group-by))]
                                    (filter identity
                                            (map (fn [k]
                                                   (if (and remove-na (nil? (map-get row k colnames)))
                                                     nil
                                                     (assoc base-map :variable k :value (map-get row k colnames))))
                                                 __merge))))
                                (m/rows data))]
      (to-dataset deshaped-data))))


(defn get-categories
  "
  Given a dataset and one or more column keys, returns the set of categories for them.

  Examples:

    (use '(incanter core datasets))
    (get-categories :eye (get-dataset :hair-eye-color))
    (get-categories [:eye :hair] (get-dataset :hair-eye-color))
  "
  ([cols data]
    (if (coll? cols)
      (for [col cols] (into #{} ($ col data)))
      (into #{} ($ cols data)))))



(defmacro with-data
  "
  Binds the given data to $data and executes the body.
  Typically used with the $ and $where functions.

  Examples:
    (use '(incanter core stats charts datasets))

    (with-data  (get-dataset :cars)
      (def lm (linear-model ($ :dist) ($ :speed)))
      (doto (scatter-plot ($ :speed) ($ :dist))
                (add-lines ($ :speed) (:fitted lm))
                 view))

     ;; create a dataset where :speed greater than 10 or less than -10
     (with-data (get-dataset :cars)
       (view (-> ($where {:speed {:$gt 20}})
                       (conj-rows ($where {:speed {:$lt 10}})))))
  "
  ([data-binding & body]
    `(binding [$data ~data-binding]
             (do ~@body))))


(defmulti to-map
  "
  Takes a dataset or matrix and returns a hash-map where the keys are
  keyword versions of the column names, for datasets, or numbers, for
  matrices, and the values are sequence of the column values.

  Examples:
    (use '(incanter core datasets))

    (to-map (get-dataset :cars))

    (to-map (matrix (range 9) 3))

  "
  dispatch)

(defmethod to-map ::dataset
  ([data]
     (ds/to-map data)))

(defmethod to-map ::matrix
  ([mat]
    (let [cols (to-list (trans mat))
          col-keys (range (ncol mat))]
      (zipmap col-keys cols))))


;; default tests for a core.matrix matrix
(defmethod to-map :default
  ([mat]
    (cond
      (m/matrix? mat)
        (throw (RuntimeException. (str "to-map multimethods not implemented for " (class mat))))
      :else
      (throw (RuntimeException. (str "to-map multimethods not implemented for " (class mat)))))))

(defn melt
  "
  Melt an object into a form suitable for easy casting, like a melt function in R.
  Only accepts one pivot key for now. e.g.

    (use '(incanter core charts datasets))
    (view (with-data (melt (get-dataset :flow-meter) :Subject)
              (line-chart :Subject :value :group-by :variable :legend true)))

  See http://www.statmethods.net/management/reshape.html for more examples."
  [dataset pivot-key]
  (let [in-m (to-map dataset)
        nrows (nrow dataset)
        ks (keys in-m)]
    (ds/dataset
      ;; column names
      [pivot-key :variable :value]
      ;; seq of rows
      (for [k ks i (range nrows) :when (not (= pivot-key k))]
        [(nth (pivot-key in-m) i) k (nth (k in-m) i)]))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; CATEGORICAL VARIABLES
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn categorical-var
  "
  Returns a categorical variable based on the values in the given collection.
  Equivalent to R's factor function.

  Options:
    :data (default nil) factors will be extracted from the given data.
    :ordered? (default false) indicates that the variable is ordinal.
    :labels (default (sort (into #{} data)))
    :levels (range (count labels))

  Examples:
    (categorical-var :data [:a :a :c :b :a :c :c])
    (categorical-var :labels [:a :b :c])
    (categorical-var :labels [:a :b :c] :levels [10 20 30])
    (categorical-var :levels [1 2 3])

  "
  ([& {:keys [data ordered? labels levels] :or {ordered? false}}]
    (let [labels (or labels
                     (if (nil? data)
                        levels
                        (sort (into #{} data))))
          levels (or levels (range (count labels)))]
      {:ordered? ordered?
       :labels labels
       :levels levels
       :to-labels (apply assoc {} (interleave levels labels))
       :to-levels (apply assoc {} (interleave labels levels))})))


(defn to-levels
  ([coll & options]
    (let [opts (when options (apply assoc {} options))
          cat-var (or (:categorical-var opts) (categorical-var :data coll))
          to-levels (:to-levels cat-var)]
      (for [label coll] (to-levels label)))))


(defn to-labels
  ([coll cat-var]
    (let [to-labels (:to-labels cat-var)]
      (for [level coll] (to-labels level)))))



(defn- get-dummies [n]
  (let [nbits (int (dec (Math/ceil (log2 n))))]
    (map #(for [i (range nbits -1 -1)] (if (bit-test % i) 1 0))
         (range n))))


(defn to-dummies [coll]
  (let [cat-var (categorical-var :data coll)
        levels (:levels cat-var)
        encoded-data (to-levels coll :categorical-var cat-var)
        bit-map (get-dummies (count levels))]
    (for [item encoded-data]
      (nth bit-map item))))


(defn- get-columns [dataset column-keys]
  (map (fn [col-key]
         (map #(% (get-column-id dataset col-key))
              (m/rows dataset)))
       column-keys))



(defn string-to-categorical [dataset column-idx dummies?]
  (let [col (m/get-column dataset column-idx)]
    (if (some #(or (string? %) (keyword? %)) col)
      (if dummies? (matrix (to-dummies col)) (matrix (to-levels col)))
      (matrix col))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn to-matrix
  "
  Converts a dataset into a matrix. Equivalent to R's as.matrix function
  for datasets.

  Options:
    :dummies (default false) -- if true converts non-numeric variables into sets
                                of binary dummy variables, otherwise converts
                                them into numeric codes.
  "
  ([dataset & {:keys [dummies] :or {dummies false}}]
     (reduce bind-columns
             (map #(string-to-categorical dataset % dummies)
                (range (count (ds/column-names dataset)))))))

;(defn- transpose-seq [coll]
;  (map (fn [idx] (map #(nth % idx) coll)) (range (count (first coll)))))




;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; GAMMA BASED FUNCTIONS FUNCTIONS
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn gamma
  "
  Equivalent to R's gamma function.

  References:
    http://incanter.org/docs/parallelcolt/api/cern/jet/stat/tdouble/Gamma.html
  "
  ([x]  (Gamma/gamma x)))


(defn beta
  "
  Equivalent to R's beta function.

  References:
    http://incanter.org/docs/parallelcolt/api/cern/jet/stat/tdouble/Gamma.html
  "
  ([a b]  (Gamma/beta a b)))


(defn incomplete-beta
  "
  Returns the non-regularized incomplete beta value.

  References:
    http://incanter.org/docs/parallelcolt/api/cern/jet/stat/tdouble/Gamma.html
  "

  ([x a b]  (* (Gamma/incompleteBeta a b x) (Gamma/beta a b))))



(defn regularized-beta
  "
  Returns the regularized incomplete beta value. Equivalent to R's pbeta function.

  References:
    http://incanter.org/docs/parallelcolt/api/cern/jet/stat/tdouble/Gamma.html
    http://en.wikipedia.org/wiki/Regularized_incomplete_beta_function
    http://mathworld.wolfram.com/RegularizedBetaFunction.html
  "
  ([x a b]
    (Gamma/incompleteBeta a b x)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; SYMMETRIC MATRIX
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn solve-quadratic
  "
  Returns a vector with the solution to x from the quadratic
  equation, a*x^2 + b*x + c.

  Arguments:
    a, b, c: coefficients of a qaudratic equation.

  Examples:
    ;; -2*x^2 + 7*x + 15
    (quadratic-formula -2 7 15)
    ;; x^2 + -2*x + 1
    (quadratic-formula 1 -2 1)

  References:
    http://en.wikipedia.org/wiki/Quadratic_formula

  "
  ([a b c]
    (let [t1 (- 0 b)
          t2 (sqrt (- (* b b) (* 4 a c)))
          t3 (* 2 a)]
      [(/ (- t1 t2) t3)
       (/ (+ t1 t2) t3)])))



(defn symmetric-matrix
  "
  Returns a symmetric matrix from the given data, which represents the lower triangular elements
  ordered by row. This is not the inverse of half-vectorize which returns a vector of the upper-triangular
  values, unless the :lower option is set to false.

  Options:
    :lower (default true) -- lower-triangular. Set :lower to false to reverse the half-vectorize function.

  Examples:

    (use 'incanter.core)
    (symmetric-matrix [1
                       2 3
                       4 5 6
                       7 8 9 10])


    (half-vectorize
      (symmetric-matrix [1
                         2 3
                         4 5 6
                         7 8 9 10] :lower false))
  "
  ([data & {:keys [lower] :or {lower true}}]
   (let [n (count data)
         p (int (second (solve-quadratic 1/2 1/2 (- 0 n))))
         mat (matrix 0 p p)
         indices (if lower
                   (for [i (range p) j (range p) :when (<= j i)] [i j])
                   (for [i (range p) j (range p) :when (<= i j)] [j i]))]
     (reduce (fn [acc idx]
               (let [[i j] (nth indices idx)]
                 (-> (m/mset acc i j (nth data idx))
                     (m/mset j i (nth data idx)))))
             (matrix 0 p p) (range n)))))

(defn toeplitz
  "
  Returns the Toeplitz matrix for the given vector, which form the first row of the matrix
  "
  ([x]
     (let [c (m/row-count x)]
       (m/compute-matrix
        [c c]
        (fn [i j] (m/mget x (abs (- i j))))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; DATA TABLE CONVERSION METHODS
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmulti data-table
  "
  Creates javax.swing.JTable from dataset or matrix.

  JTable column names for datasets will be the datset's column names. For
  matrices, an optional argument :column-names can be used to set the resulting
  column names. Otherweise incrementing indices are used (0,1,2,..).

  Example:

  (data-table (clojure.core.matrix/matrix [[1 2 3][4 5 6]])
    :column-names [\"first col\" \"second col\" \"third col\"])
  "
  (fn [obj & options]
    (dispatch obj)))

(defmethod data-table ::matrix
  [obj & {:keys [column-names]}]
    (let [col-names (or column-names (range (ncol obj)))
          m (ncol obj)
          n (nrow obj)]
      (JTable.
        (cond
          (and (> m 1) (> n 1))
          (Vector. (map #(Vector. %) (to-list obj)))
          (or (and (> m 1) (= n 1)) (and (= m 1) (= n 1)))
          (Vector. (map #(Vector. %) [(to-list obj) []]))
          (and (= m 1) (> n 1))
          (Vector. (map #(Vector. [%]) (to-list obj))))
        (Vector. col-names))))

(defmethod data-table ::dataset
  [obj & options]
   (let [col-names (ds/column-names obj)
         column-vals (map (fn [row] (map #(row %) col-names)) (ds/row-maps obj))
         table-model (javax.swing.table.DefaultTableModel.
                       (java.util.Vector. (map #(java.util.Vector. %) column-vals))
                       (java.util.Vector. col-names))]
    (JTable. table-model)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; VIEW METHODS
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmulti view
  "
  This is a general 'view' function. When given an Incanter matrix/dataset
  or a Clojure numeric collection, it will display it in a Java Swing
  JTable. When given an Incanter chart object, it will display it in a new
  window. When given a URL string, it will open the location with the
  platform's default web browser.

  When viewing charts, a :width (default 500) and :height (default 400)
  option can be provided.

  When viewing an incanter.processing sketch, set the :exit-on-close option
  to true (default is false) to kill the animation processes when you
  close the window (this will also kill your REPL or Swank server),
  otherwise those processing will continue to run in the background.



  Examples:

    (use '(incanter core stats datasets charts))

    ;; view matrices
    (def rand-mat (matrix (sample-normal 100) 4))
    (view rand-mat)

    ;; view numeric collections
    (view [1 2 3 4 5])
    (view (sample-normal 100))

    ;; view Incanter datasets
    (view (get-dataset :iris))

    ;; convert dataset to matrix, changing Species names to numeric codes
    (view (to-matrix (get-dataset :iris)))

    ;; convert dataset to matrix, changing Species names to dummy variables
    (view (to-matrix (get-dataset :iris) :dummies true))

    ;; view a chart
    (view (histogram (sample-normal 1000)) :width 700 :height 700)

    ;; view a URL
    (view \"http://incanter.org\")

    ;; view a PNG file
    (save (histogram (sample-normal 1000)) \"/tmp/norm_hist.png\")
    (view \"file:///tmp/norm_hist.png\")
  "
  (fn [obj & options]
    (dispatch obj)))

(defmethod view ::coll
  [obj & options]
  (apply view (m/transpose (matrix [obj])) options))

(defmethod view ::vector
  [obj & options]
  (apply view (m/transpose (matrix [obj])) options))

(defmethod view ::matrix
  [obj & options]
  (apply view (apply data-table obj options) options))

(defmethod view ::dataset
  [obj & options]
  (apply view (apply data-table obj options) options))

(defmethod view javax.swing.JTable
  [obj & {:keys [title] :or {title "Incanter Table"}}]
  (doto (javax.swing.JFrame. title)
    (.add (javax.swing.JScrollPane. obj))
    (.setSize 500 600)
    (.setVisible true)))

(defmethod view java.awt.Image
  [obj & options]
    (let [icon (javax.swing.ImageIcon. obj)
          label (javax.swing.JLabel. icon)
          height (+ 15 (.getIconHeight icon))
          width (+ 15 (.getIconWidth icon))]
      (doto (javax.swing.JFrame. "Incanter Image")
        (.add (javax.swing.JScrollPane. label))
        (.setSize height width)
        .pack
        (.setVisible true))))


;; URL view method code lifted from clojure.contrib.javadoc.browse/open-url-in-browser
(defmethod view String
  ([url]
    (try
      (when (clojure.lang.Reflector/invokeStaticMethod "java.awt.Desktop" "isDesktopSupported" (to-array nil))
        (-> (clojure.lang.Reflector/invokeStaticMethod "java.awt.Desktop" "getDesktop" (to-array nil))
            (.browse (java.net.URI. url)))
        url)
      (catch ClassNotFoundException e nil))))

;;fixed, was erroneously returning the dispatch function
;;instead of applying it to obj..
(defmulti set-data
  "
  Examples:

    (use '(incanter core charts datasets))

    (def data (get-dataset :iris))
    (def table (data-table data))
    (view table)
    ;; now view only a subset of the data
    (set-data table ($where {:Petal.Length {:gt 6}} data))


    ;; use sliders to dynamically select the query values
    (let [data (get-dataset :iris)
          table (data-table data)]
      (view table)
      (sliders [species [\"setosa\" \"virginica\" \"versicolor\"]
                min-petal-length (range 0 8 0.1)]
        (set-data table ($where {:Species species
                                 :Petal.Length {:gt min-petal-length}}
                                data))))

  "
  (fn [obj & more] (dispatch obj)))

;;note: the clojure.core.matrix.impl.dataset.DataSetRow was tossing
;;an error originally, "after" fixing the problems with set-data's
;;dispatch function.  rows don't implement IFn, and were being invoked
;;as if ds/row-maps.  datatable only cares about values, so mapping
;;seq over the rows works fine.  seq is also the only way to
;;coerce a row into a range of values, even though print method
;;leads you to believe you have a :values an accessible key.
;;DatasetRow doesn't implement ILookup...
(defmethod set-data javax.swing.JTable
  ([table data]
     (let [col-names   (ds/column-names data)
           column-vals (m/rows data)
           table-model (javax.swing.table.DefaultTableModel. (java.util.Vector. (map #(java.util.Vector. (seq %)) column-vals))
                                                             (java.util.Vector. col-names))]
       (.setModel table table-model))))



(defn quit
  "Exits the Clojure shell."
  ([] (System/exit 0)))


(defn- count-types
  "
  Helper function. Takes in a seq (usually from a column from an Incanter dataset)
  and returns a map of types -> counts of the occurrence of each type
  "
  ([my-col]
    (reduce
      (fn [counts x]
        (let [t (type x) c (get counts t)] (assoc counts t (inc (if (nil? c) 0 c)))))
      {}
      my-col)))


(defn- count-col-types
  "
  Takes in a column name or number and a dataset. Returns a raw count
  of each type present in that column. Counts nils."
  ([col ds]
    (count-types ($ col ds))))


(defmulti save
  "
  Save is a multi-function that is used to write matrices, datasets and
  charts (in png format) to a file.

  Arguments:
    obj -- is a matrix, dataset, or chart object
    filename -- the filename to create.

  Matrix and dataset options:
    :delim (default \\,) column delimiter
    :header (default nil) an sequence of strings to be used as header line
        for matrices the default value is nil, for datasets, the default is
        the dataset's column-names array.
    :append (default false) determines whether this given file should be
        appended to. If true, a header will not be written to the file again.
    If the filename is exactly \"-\" then *out* the matrix/dataset will be
        written to *out*

  Chart options:
    :width (default 500)
    :height (default 400)


  Matrix Examples:

    (use '(incanter core io))
    (def A (matrix (range 12) 3)) ; creates a 3x4 matrix
    (save A \"A.dat\") ; writes A to the file A.dat, with no header and comma delimited
    (save A \"A.dat\" :delim \\tab) ; writes A to the file A.dat, with no header and tab delimited

    ;; writes A to the file A.dat, with a header and tab delimited
    (save A \"A.dat\" :delim \\, :header [\"col1\" \"col2\" \"col3\"])


  Dataset Example:

    (use '(incanter core io datasets))
    ;; read the iris sample dataset, and save it to a file.
    (def iris (get-dataset :iris))
    (save iris \"iris.dat\")


  Chart Example:

    (use '(incanter core io stats charts))
    (save (histogram (sample-normal 1000)) \"hist.png\")

    ;; chart example using java.io.OutputStream instead of filename
    (use '(incanter core stats charts))
    (import 'java.io.FileOutputStream)
    (def fos (FileOutputStream. \"/tmp/hist.png\"))
    (def hist (histogram (sample-normal 1000)))
    (save hist fos)
    (.close fos)

    (view \"file:///tmp/hist.png\")


  "
  (fn [obj filename & options]
    (if (.contains (str (type obj)) "processing.core.PApplet")
      :sketch
      (dispatch obj))))




(defn grid-apply
  "Applies the given function f, that accepts two arguments, to a grid
  defined by rectangle bounded x-min, y-min, x-max, y-max and returns a
  sequence of three sequences representing the cartesian product of x and y
  and z calculated by applying f to the combinations of x and y.
  
  Defaults to a 100x100 'grid', where the x and y axes are sampled
  evenly accorinding to the grid.  Callers may supply their own
  horizontal and vertical sampling resolutions via x-res and
  y-res.  In cases where the sampling resolution is lower
  than the range of actual values, a sparse response
  surface will result.
  "
  ([f x-min x-max y-min y-max x-res y-res]
    (let [x-vals (range x-min x-max (/ (- x-max x-min) x-res))
          y-vals (range y-min y-max (/ (- y-max y-min) y-res))
          xyz (for [_x x-vals _y y-vals] [_x _y (f _x _y)])
          transpose #(list (conj (first %1) (first %2))
                           (conj (second %1) (second %2))
                           (conj (nth %1 2) (nth %2 2)))]
      (reduce transpose [[] [] []] xyz)))
  ([f x-min x-max y-min y-max]
   (grid-apply f x-min x-max y-min y-max 100 100)))




(defop '- 60 'incanter.core/minus)
(defop '+ 60 'incanter.core/plus)
(defop '/ 80 'incanter.core/div)
(defop '* 80 'incanter.core/mult)
(defop '<*> 80 'incanter.core/mmult)
(defop '<x> 80 'incanter.core/kronecker)
(defop '** 100 'incanter.core/pow)

(defmacro $=
  "
  Formula macro translates from infix to prefix


  Examples:

    (use 'incanter.core)
    ($= 7 + 8)
    ($= [1 2 3] + [4 5 6])
    ($= [1 2 3] + (sin [4 5 6]))
    ($= [1 2 3] <*> (trans [1 2 3]))
    ($= [1 2 3] * [1 2 3])
    ($= [1 2 3] <x> [1 2 3])
    ($= 9 * 8 ** 3)
    ($= (sin Math/PI) * 10)

    ($= 10 + 20 * (4 - 5) / 6)

    ($= 20 * (4 - 5) / 6)

    (let [x 10
          y -5]
      ($= x + y / -10))

    ($= 3 ** 3)

    ($= [1 2 3] * [1 2 3])
    ($= [1 2 3] / (sq [1 2 3]) + [5 6 7])

    ($= (sqrt 5 * 5 + 3 * 3))
    ($= (sq [1 2 3] + [1 2 3]))
    ($= ((5 + 4) * 5))
    ($= ((5 + 4 * (3 - 4)) / (5 + 8) * 6))
    ($= [1 2 3] + 5)
    ($= (matrix [[1 2] [4 5]]) + 6)
    ($= (trans [[1 2] [4 5]]) + 6)

    ($= (trans [[1 2] [4 5]]) <*> (matrix [[1 2] [4 5]]))


    (use '(incanter core charts))
    (defn f [x] ($= x ** 2 + 3 * x + 5))
    (f 5)
    (view (function-plot f -10 10))
    (view (function-plot #($= % ** 2 + 3 * % + 5) -10 10))
    (view (function-plot (fn [x] ($= x ** 2 + 3 * x + 5)) -10 10))
    (let [x (range -10 10 0.1)]
      (view (xy-plot x ($= x ** 3 - 5 * x ** 2 + 3 * x + 5))))

    ($= (5 + 7))
    ($= (trans [1 2 3 4]) <*> [1 2 3 4])
    ($= [1 2 3 4] <*> (trans [1 2 3 4]))

    ($= [1 2 3 4] <*> (trans [1 2 3 4]))
    ($= [1 2 3 4] <x> (trans [1 2 3 4]))


    ;; kronecker product example
    ($= (matrix [[1 2] [3 4] [5 6]]) <x> 4)
    ($= (matrix [[1 2] [3 4] [5 6]]) <x> (matrix [[1 2] [3 4]]))
    ($= [1 2 3 4] <x> 4)

    ($= 3 > (5 * 2/7))

    (use '(incanter core datasets charts))
    (with-data (get-dataset :cars)
      (doto (scatter-plot :speed :dist :data ($where ($fn [speed dist] ($= dist / speed < 2))))
        (add-points :speed :dist :data ($where ($fn [speed dist] ($= dist / speed >= 2))))
        (add-lines ($ :speed) ($= 2 * ($ :speed)))
        view))

  "
  ([& equation]
    (infix-to-prefix equation)))


;; PRINT METHOD FOR INCANTER DATASETS
(defmethod print-method clojure.core.matrix.impl.dataset.DataSet
  [o, ^java.io.Writer w]
  (binding [*out* w]
    (print-table (ds/column-names o) (ds/row-maps o))))

;;Can we implement these in core.matrix and ditch
;;incanter.Matrix class entirely?
(comment ;; TODO
  (defn- block-diag2 [block0 block1]
    (.composeDiagonal DoubleFactory2D/dense block0 block1))
  (defn block-diag
    "Blocks should be a sequence of matrices."
    [blocks]
    (new Matrix (reduce block-diag2 blocks)))

  (defn block-matrix
    "Blocks should be a nested sequence of matrices. Each element of the sequence should be a block row."
    [blocks]
    (let [element-class (-> blocks first first class)
          native-rows (for [row blocks] (into-array element-class row))
          native-blocks (into-array (-> native-rows first class) native-rows)]
      (new Matrix (.compose DoubleFactory2D/dense native-blocks))))

  (defn separate-blocks
    "Partitions should be a sequence of [start,size] pairs."
    [matrix partitions]
    (for [p partitions]
      (for [q partitions]
        (.viewPart matrix (first p) (first q) (second p) (second q)))))

  (defn diagonal-blocks
    "Partitions should be a sequence of [start,size] pairs."
    [matrix partitions]
    (for [p partitions]
      (.viewPart matrix (first p) (first p) (second p) (second p)))))

(defn ^:deprecated reorder-columns
  "
  Produce a new dataset with the columns in the specified order.
  Returns nil if no valid column names are given.

  Deprecated. Please use clojure.core.matrix.dataset/select-columns instead"
  [ds cols]
  (let [col-set (into #{} (ds/column-names ds))]
    (if (clojure.set/subset? col-set (into #{} cols))
      (ds/select-columns ds cols)
      nil)))
