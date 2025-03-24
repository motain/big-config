(ns big-config.run-test
  (:require
   [big-config :as bc]
   [big-config.run :as run :refer [run-cmds]]
   [clojure.test :refer [deftest is testing]]))

(deftest run-cmds-test
  (testing "with 3 commands"
    (let [expect {:big-config/env :repl, :big-config.run/shell-opts {:continue true}, :big-config.run/cmds '("echo three"), :big-config/procs [{:exit 0, :out "one\n", :err "", :cmd ["echo" "one"]} {:exit 0, :out "two\n", :err "", :cmd ["echo" "two"]} {:exit 0, :out "three\n", :err "", :cmd ["echo" "three"]}], :big-config/exit 0, :big-config/err ""}
          actual (run-cmds {::bc/env :repl
                            ::run/shell-opts {:continue true}
                            ::run/cmds ["echo one"
                                        "echo two"
                                        "echo three"]})]

      (is (= expect actual)))))
