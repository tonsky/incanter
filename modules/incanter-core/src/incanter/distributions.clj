;;; distributions.clj -- A common distribution protocol with several implmentations.

;; by Mark Fredrickson http://www.markmfredrickson.com
;; May 10, 2010

;; Copyright (c) David Edgar Liebke, 2009. All rights reserved.  The use
;; and distribution terms for this software are covered by the Eclipse
;; Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.htincanter.at the root of this
;; distribution.  By using this software in any fashion, you are
;; agreeing to be bound by the terms of this license.  You must not
;; remove this notice, or any other, from this software.

;; CHANGE LOG



(ns #^{:doc "Distributions. TODO: provide a useful string" :author "Mark M. Fredrickson"}
	incanter.distributions)

(defprotocol Distribution
	"The distribution protocol defines operations on probability distributions.
	 Distributions may be univariate (defined over scalars) or multivariate
	 (defined over vectors). Distributions may be discrete or continuous."
	(pdf [d v] "Returns the value of the probability density/mass function for the d at support v")
	(cdf [d v] "Returns the value of the cumulative distribution function for the distribution at support v")
	(draw [d] [d n] "Returns 1 or n samples drawn from d")
  (support [d] "Returns the support of d in the form of XYZ"))
;; Notes: other possible methods include moment generating function, transformations/change of vars

(defn- tabulate
  "Private tabulation function that works on any data type, not just numerical"
  [v]
  (let [f (frequencies v)
        total (reduce + (vals f))]
    (into {} (map (fn [[k v]] [k (/ v total)]) f))))

;; Extending some common types to be distributions
;; vectors are tabulated for total counts
(extend-type clojure.lang.PersistentVector
	Distribution
		(pdf [d v] (get v (tabulate v) 0))
		(cdf [d v] nil)
		(draw [d] (nth d (rand-int (count d))))
    (draw [d n] (repeatedly n #(draw d))) 
		(support [d] (keys (frequencies d))))
