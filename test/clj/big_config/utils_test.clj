(ns big-config.utils-test
  (:require
   [big-config :as bc]
   [big-config.core :refer [->workflow step->workflow]]
   [big-config.run :as run]
   [clojure.test :refer [deftest is testing]]))

(defn test-step-fn [end-steps xs f step opts]
  (when (end-steps step)
    (swap! xs conj opts))
  (f step opts))

(def default-opts #:big-config.lock {:aws-account-id "111111111111"
                                     :region "eu-west-1"
                                     :ns "test.module"
                                     :fn "invoke"
                                     :owner "CI"
                                     :isolation (or (System/getenv "ZELLIJ_SESSION_NAME") "CI")
                                     :lock-keys [:big-config.lock/aws-account-id
                                                 :big-config.lock/region
                                                 :big-config.lock/ns
                                                 ;; to avoid to conflict with GitHub Actions and other develoers
                                                 :big-config.lock/isolation]
                                     ::run/run-cmd "true"
                                     ::bc/test-mode true
                                     ::bc/env :repl})

(deftest step->workflow-test
  (testing "step->workflow"
    (let [expected {:big-config.utils-test/foo :bar, :big-config/exit 1, :big-config/err "Error"}
          f (fn [opts]
              (merge opts
                     {::bc/exit 1
                      ::bc/err "Err"}))]
      (as-> ((step->workflow f ::foo "Error") {::foo :bar}) $
        (is (= expected $))))))

(defn ^:export a-step-fn [f step opts]
  (let [opts (update opts ::bc/steps (fnil conj []) [step :start-a])
        opts (f step opts)]
    (update opts ::bc/steps (fnil conj []) [step :end-a])))

(defn b-step-fn [f step opts]
  (let [opts (update opts ::bc/steps (fnil conj []) [step :start-b])
        opts (f step opts)]
    (update opts ::bc/steps (fnil conj []) [step :end-b])))

(deftest step-fns-test
  (testing "step-fns by name and by symbol"
    (let [expect {:big-config/err nil, :big-config/exit 0, :big-config/steps [[:big-config.utils-test/start :start-a] [:big-config.utils-test/start :start-b] [:big-config.utils-test/start :end-b] [:big-config.utils-test/start :end-a] [:big-config.utils-test/end :start-a] [:big-config.utils-test/end :start-b] [:big-config.utils-test/end :end-b] [:big-config.utils-test/end :end-a]], :big-config.utils-test/bar :baz}
          actual (->> ((->workflow {:first-step ::start
                                    :step-fns ["big-config.utils-test/a-step-fn"
                                               b-step-fn]
                                    :wire-fn (fn [step _]
                                               (case step
                                                 ::start [#(merge % {::bc/exit 0
                                                                     ::bc/err nil}) ::end]
                                                 ::end [identity]))
                                    :next-fn ::end}) {::bar :baz})
                      (into (sorted-map)))]
      (is (= expect actual)))))

#_(->> ((->workflow {:first-step ::foo
                     :wire-fn (fn [step _]
                                (case step
                                  ::foo [#(throw (Exception. "Java exception") #_%) ::end]
                                  ::end [identity]))
                     :next-fn ::end}) {::bar :baz})
       (into (sorted-map)))

#_(->> ((->workflow {:first-step ::foo
                     :wire-fn (fn [step _]
                                (case step
                                  ::foo [#(throw (ex-info "Error" %)) ::end]
                                  ::end [identity]))
                     :next-fn ::end})
        {::bar :baz})
       (into (sorted-map)))
