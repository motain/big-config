(ns big-config.main
  (:require
   [babashka.process :as process]
   [big-config.git :as git]
   [big-config.lock :as lock]
   [big-config.spec :as bs]
   [big-config.utils :as utils :refer [description-for-step error-for-step]]
   [cheshire.core :as json]
   [clojure.spec.alpha :as s]))

(defn print-and-flush
  [res]
  (println res)
  (flush))

(defn ^:export create [args]
  {:pre [(s/valid? ::bs/create args)]}
  (let [{:keys [fn ns]} args]
    (-> (format "%s/%s" ns fn)
        (symbol)
        requiring-resolve
        (apply (vector args))
        (json/generate-string {:pretty true})
        print-and-flush)))

(defn run-cmd [opts]
  (let [{:keys [run-cmd]} opts
        res (process/shell {:continue true} run-cmd)]
    (update opts :cmd-results (fnil conj []) res)))

(defn git-push [opts]
  (utils/generic-cmd opts "git push"))

(defn ^:export acquire-lock [opts]
  #_{:clj-kondo/ignore [:loop-without-recur]}
  (loop [step :lock-acquire
         opts opts]
    (println (description-for-step step))
    (let [opts (update opts :steps (fnil conj []) step)
          error-msg (error-for-step step)]
      (case step
        :lock-acquire (as-> (lock/acquire opts) $
                        (utils/recur-ok-or-end :exit $ error-msg))
        :exit opts))))

(defn ^:export release-any-onwer [opts]
  #_{:clj-kondo/ignore [:loop-without-recur]}
  (loop [step :lock-acquire
         opts opts]
    (println (description-for-step step))
    (let [opts (update opts :steps (fnil conj []) step)
          error-msg (error-for-step step)]
      (case step
        :lock-release-all (as-> (lock/release-any-owner opts) $
                            (utils/recur-ok-or-end :exit $ error-msg))
        :exit opts))))

(defn ^:export run-with-lock [opts]
  #_{:clj-kondo/ignore [:loop-without-recur]}
  (loop [step :lock-acquire
         opts opts]
    (println (description-for-step step))
    (let [opts (update opts :steps (fnil conj []) step)
          error-msg (error-for-step step)]
      (case step
        :lock-acquire (as-> (lock/acquire opts) $
                        (utils/recur-ok-or-end :git-check $))
        :git-check (as-> (git/check opts) $
                     (utils/recur-ok-or-end :run-cmd $))
        :run-cmd (as-> (run-cmd opts) $
                   (utils/recur-ok-or-end :git-push $ error-msg))
        :git-push (as-> (git-push opts) $
                    (utils/recur-ok-or-end :lock-release $ error-msg))
        :lock-release (lock/release-any-owner opts)))))

(comment
  (create {:aws-account-id "251213589273"
           :region "eu-west-1"
           :ns "tofu.module-a.main"
           :fn "invoke"})
  #_{:clj-kondo/ignore [:unused-binding]}
  (let [opts {:aws-account-id "251213589273"
              :region "eu-west-1"
              :ns "tofu.module-a.main"
              :fn "invoke"
              :owner "ALBERTO_MACO"
              :lock-keys [:aws-account-id :region :ns]
              :run-cmd "false"}]))
