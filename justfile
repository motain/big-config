help:
    @just -f {{ justfile() }} --list --unsorted

# check the AWS identity
[group('tofu')]
get-caller-identity aws-account-id:
    cd tofu/{{ aws-account-id }} && \
    direnv exec . aws sts get-caller-identity

# tofu acquire lock
[group('tofu')]
lock-acquire aws-account-id region ns owner:
    @clj -X big-config.main/acquire-lock \
      :aws-account-id \"{{ aws-account-id }}\" \
      :region \"{{ region }}\" \
      :ns \"{{ ns }}\" \
      :owner \"{{ owner }}\" \
      :lock-keys '[:aws-account-id :region :ns]'

# tofu release lock
[group('tofu')]
lock-release-any-owner aws-account-id region ns owner:
    @clj -X big-config.main/release-lock-any-owner \
      :aws-account-id \"{{ aws-account-id }}\" \
      :region \"{{ region }}\" \
      :ns \"{{ ns }}\" \
      :owner \"{{ owner }}\" \
      :lock-keys '[:aws-account-id :region :ns]'

# tofu facade
[group('tofu')]
tofu cmd module profile:
    @clj -X:dev big-config.main/tofu-facade \
      :args '["{{ cmd }}" :{{ module }} :{{ profile }}]'
