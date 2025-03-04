(ns utils)

(def env :prod)

(defn exit-with-code? [n opts]
  (when (= env :prod)
    (shutdown-agents)
    (flush)
    (System/exit n))
  (assoc opts :exit n))

(defn handle-last-cmd [opts]
  (let [{:keys [cmd-results]} opts]
    (last cmd-results)))

(defmacro recur-with-no-error
  ([key opts]
   `(recur-with-no-error ~key ~opts nil))
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

(defmacro recur-with-error [key opts]
  `(let [proc# (handle-last-cmd ~opts)
         exit# (get proc# :exit)]
     (if (= exit# 0)
       (do
         (println "Success")
         (exit-with-code? 0 ~opts))
       (recur ~key ~opts))))

(comment
  (alter-var-root #'env (constantly :test)))
