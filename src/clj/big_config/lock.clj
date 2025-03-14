(ns big-config.lock
  (:require
   [babashka.process :as process]
   [big-config.unlock :as unlock]
   [big-config.utils :as utils :refer [choice default-step-fn generic-cmd
                                       handle-cmd nested-sort-map]]
   [buddy.core.codecs :as codecs]
   [buddy.core.hash :as hash]
   [clojure.edn :as edn]
   [clojure.string :as str]))

(defn generate-lock-id [opts]
  (let [{:keys [lock-keys owner]} opts
        lock-details (select-keys opts lock-keys)
        lock-name (-> lock-details
                      nested-sort-map
                      pr-str
                      hash/sha256
                      codecs/bytes->hex
                      str/upper-case
                      (subs 0 4)
                      (as-> $ (str "LOCK-" $)))]
    (-> opts
        (assoc :lock-details lock-details)
        (update :lock-details assoc :owner owner)
        (merge {:lock-name lock-name
                :exit 0
                :err nil}))))

(defn delete-tag [opts]
  (let [{:keys [lock-name]} opts]
    (generic-cmd opts (format "git tag -d %s" lock-name))))

(defn create-tag [opts]
  (let [{:keys [lock-name
                lock-details]} opts
        proc (-> (process/shell {:continue true
                                 :in (pr-str lock-details)
                                 :out :string
                                 :err :string} (format "git tag -a %s -F -" lock-name)))]
    (handle-cmd opts proc)))

(defn push-tag [opts]
  (let [{:keys [lock-name]} opts]
    (generic-cmd opts (format "git push origin %s" lock-name))))

(defn delete-remote-tag [opts]
  (let [{:keys [lock-name]} opts]
    (generic-cmd opts (format "git push --delete origin %s" lock-name))))

(defn get-remote-tag [opts]
  (let [{:keys [lock-name]} opts]
    (generic-cmd opts (format "git fetch origin tag %s --no-tags" lock-name))))

(defn read-tag [opts]
  (let [{:keys [lock-name]} opts
        cmd (format "git cat-file -p %s" lock-name)]
    (generic-cmd opts cmd :tag-content)))

(defn parse-tag-content [tag-content]
  (->> tag-content
       str/split-lines
       (filter #(str/starts-with? % "{"))
       first
       edn/read-string))

(defn check-tag [opts]
  (let [{:keys [tag-content]} opts
        ownership (every? (fn [[k v]]
                            (= (get opts k) v))
                          (parse-tag-content tag-content))]
    (merge opts (if ownership
                  {:exit 0
                   :err nil}
                  {:exit 1
                   :err "Different owner"}))))

(defn check-remote-tag [opts]
  (let [{:keys [lock-name]} opts
        cmd (format "git ls-remote --exit-code origin  refs/tags/%s" lock-name)
        {:keys [exit] :as opts} (generic-cmd opts cmd)]
    (when (= exit 2)
      (assoc opts :exit 0))))

(defn lock
  ([opts]
   (lock default-step-fn opts))
  ([step-fn opts]
   #_{:clj-kondo/ignore [:loop-without-recur]}
   (loop [step ::generate-lock-id
          opts opts]
     (let [[f next-step] (case step
                           ::generate-lock-id [generate-lock-id ::delete-tag]
                           ::delete-tag [delete-tag ::create-tag]
                           ::create-tag [create-tag ::push-tag]
                           ::push-tag [push-tag ::get-remote-tag]
                           ::get-remote-tag [(comp get-remote-tag delete-tag) ::read-tag]
                           ::read-tag [read-tag ::check-tag]
                           ::check-tag [check-tag ::end]
                           ::end [identity nil])]
       (as-> (step-fn {:f f
                       :step step
                       :opts opts}) $
         (case step
           ::end $
           ::push-tag (choice {:on-success ::end
                               :on-failure next-step
                               :opts $})
           ::delete-tag (recur next-step $)
           (choice {:on-success next-step
                    :on-failure ::end
                    :opts $})))))))

(defn unlock-any
  ([opts]
   (unlock-any default-step-fn opts))
  ([step-fn opts]
   #_{:clj-kondo/ignore [:loop-without-recur]}
   (loop [step ::unlock/generate-lock-id
          opts opts]
     (let [[f next-step] (case step
                           ::unlock/generate-lock-id [generate-lock-id ::unlock/delete-tag]
                           ::unlock/delete-tag [delete-tag ::unlock/delete-remote-tag]
                           ::unlock/delete-remote-tag [delete-remote-tag ::unlock/check-remote-tag]
                           ::unlock/check-remote-tag [check-remote-tag ::unlock/end]
                           ::unlock/end [identity nil])]
       (as-> (step-fn {:f f
                       :step step
                       :opts opts}) $
         (if next-step
           (recur next-step $)
           $))))))

(comment)
