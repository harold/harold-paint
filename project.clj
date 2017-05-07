(defproject harold-paint "0.1.0-SNAPSHOT"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.9.229"]
                 [reagent "0.6.1"]]

  :min-lein-version "2.5.3"

  :source-paths ["src/clj"]

  :plugins [[lein-cljsbuild "1.1.4"]
            [lein-ancient "0.6.10"]]

  :clean-targets ^{:protect false} ["target"
                                    "resources/public/js/compiled"]

  :figwheel {:css-dirs ["resources/public/css"]}

  :profiles {:dev {:plugins [[lein-figwheel "0.5.10"]]}}

  :cljsbuild {:builds [{:id           "dev"
                        :source-paths ["src/cljs"]
                        :figwheel     {:on-jsload "harold-paint.core/reload"}
                        :compiler     {:main                 harold-paint.core
                                       :optimizations        :none
                                       :output-to            "resources/public/js/compiled/app.js"
                                       :output-dir           "resources/public/js/compiled/dev"
                                       :asset-path           "js/compiled/dev"
                                       :source-map-timestamp true}}
                       {:id           "min"
                        :source-paths ["src/cljs"]
                        :compiler     {:main          harold-paint.core
                                       :optimizations :advanced
                                       :output-to     "resources/public/js/compiled/app.js"
                                       :output-dir    "resources/public/js/compiled/min"
                                       :elide-asserts true
                                       :pretty-print  false}}]})
