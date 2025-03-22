help:
    @just -f {{ justfile() }} --list --unsorted

# clean before tofu init
[group('tofu')]
clean:
    rm -rf tofu/251213589273/eu-west-1/tofu.module-a.main/.terraform/

# check the AWS identity
[group('tofu')]
get-caller-identity aws-account-id:
    cd tofu/{{ aws-account-id }} && \
    direnv exec . aws sts get-caller-identity

# tofu opts|init|plan|apply|destroy|lock|unlock-any
[group('tofu')]
tofu action module profile:
    #!/usr/bin/env bb
    (require '[big-config.tofu :refer [main]])
    (main {:args [:{{ action }} :{{ module }} :{{ profile }}]})
