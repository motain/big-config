(ns tofu.common.create-provider)

(defn invoke [{:keys [aws-account-id region bucket module]}]
  (let [key (str (name module) ".tfstate")]
    {:provider {:aws [{:profile aws-account-id
                       :region region
                       :allowed_account_ids (vector aws-account-id)}]}
     :terraform [{:backend {:s3 [{:bucket bucket
                                  :encrypt true
                                  :key key
                                  :region region}]}
                  :required_providers [{:aws {:source "hashicorp/aws"
                                              :version "~> 5.0"}}]
                  :required_version ">= 1.8.0"}]}))
