(defproject io.github.tonsky/incanter-core "1.9.5"
  :description "Incanter-core is the core module of the Incanter project."
  :url "http://incanter.org/"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :scm {:name "git"
        :url "https://github.com/incanter/incanter"
        :dir "modules/incanter-core"}
  :min-lein-version "2.0.0"
  :java-source-paths ["java"]
  :javac-options ["--release" "8"]
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/math.combinatorics "0.1.4" :exclusions [org.clojure/clojure]]
                 [net.mikera/vectorz-clj "0.44.1" :exclusions [org.clojure/clojure]]
                 [net.mikera/core.matrix "0.52.0" :exclusions [org.clojure/clojure]]
                 [net.sourceforge.parallelcolt/parallelcolt "0.10.1"]]
  :profiles {:dev {:dependencies [[clatrix "0.5.0" :exclusions [org.clojure/clojure net.mikera/core.matrix]]
                                  [org.jblas/jblas "1.2.3"]]}}
  :deploy-repositories
  {"clojars"
   {:url "https://clojars.org/repo"
    :username "tonsky"
    :password :env/clojars_token
    :sign-releases false}}
  )
