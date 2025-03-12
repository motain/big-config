help:
    @just -f {{ justfile() }} --list --unsorted

# clean before tofu init
[group('tofu')]
clean:
    cd tofu/251213589273/eu-west-1 \
    && rm -rf tofu.module-a.main \
    && mkdir tofu.module-a.main

# check the AWS identity
[group('tofu')]
get-caller-identity aws-account-id:
    cd tofu/{{ aws-account-id }} && \
    direnv exec . aws sts get-caller-identity

# tofu init|plan|apply|destroy|lock|unlock-any
[group('tofu')]
tofu cmd module profile:
    @clj -X:dev big-config.main/tofu \
      :args '[:{{ cmd }} :{{ module }} :{{ profile }}]'
