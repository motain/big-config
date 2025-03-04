(ns git
  (:require
   [babashka.process :as process]))

(def env :prod)

(defn get-revision [opts revision key]
  (let [res (-> (process/shell {:continue true
                                :out :string
                                :err :string}
                               (format "git rev-parse %s" revision)))]

    (-> opts
        (assoc key (:out res))
        (update :cmd-results (fnil conj []) res))))

(defn fetch-origin [opts]
  (let [res (-> (process/shell {:continue true
                                :out :string
                                :err :string}
                               "git fetch origin"))]
    (update opts :cmd-results (fnil conj []) res)))

(defn git-diff [opts]
  (let [res (-> (process/shell {:continue true
                                :out :string
                                :err :string}
                               "git diff --quiet"))]
    (update opts :cmd-results (fnil conj []) res)))

(defn exit-with-code? [n opts]
  (when (= env :prod)
    (shutdown-agents)
    (flush)
    (System/exit n))
  (assoc opts :exit n))

(defn handle-last-cmd [opts]
  (let [{:keys [cmd-results]} opts]
    (last cmd-results)))

(defmacro recur?
  ([key opts]
   `(recur? ~key ~opts nil))
  ([key opts msg]
   `(let [proc# (handle-last-cmd ~opts)
          exit# (get proc# :exit)
          err# (get proc# :err)
          msg# (if ~msg
                 ~msg
                 err#)]
      (if (= exit# 0)
        (recur ~key ~opts)
        (do
          (println msg#)
          (exit-with-code? 1 ~opts))))))

(defn ^:export check [_]
  #_{:clj-kondo/ignore [:loop-without-recur]}
  (loop [step :git-diff
         opts {}]
    (let [opts (update opts :steps (fnil conj []) step)]
      (case step
        :git-diff (as-> (git-diff opts) $
                    (recur? :fetch-origin $ "The repo is dirty"))
        :fetch-origin (as-> (fetch-origin opts) $
                        (recur? :prev-revision $))
        :prev-revision (as-> (get-revision opts "HEAD~1" :prev-revision) $
                         (recur? :origin-revision $))
        :origin-revision (as-> (get-revision opts "origin/main" :origin-revision) $
                           (recur? :compare-revisions $))
        :compare-revisions (let [{:keys [prev-revision origin-revision]} opts]
                             (if (= prev-revision origin-revision)
                               (exit-with-code? 0 opts)
                               (exit-with-code? 1 opts)))))))
