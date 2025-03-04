(ns utils
  (:require
   [babashka.process :as process]
   [clojure.string :as str]))

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

(def default-opts {:continue true
                   :out :string
                   :err :string})

(defn generic-cmd
  ([opts cmd]
   (let [res (process/shell default-opts cmd)]
     (update opts :cmd-results (fnil conj []) res)))
  ([opts cmd key]
   (let [res (process/shell default-opts cmd)]
     (-> opts
         (assoc key (-> (:out res)
                        str/trim-newline))
         (update :cmd-results (fnil conj []) res)))))

(comment
  (alter-var-root #'env (constantly :test)))
