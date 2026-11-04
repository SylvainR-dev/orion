# Gestion de l'instance Orion (k3s) : allumer, eteindre, installer.
.RECIPEPREFIX = >
REGION := eu-west-3
NAME   := orion-k3s
KEY    := $(HOME)/.ssh/orion-ec2-key
SOCK   := /tmp/orion-k3s-tunnel.sock

ID_CMD = aws ec2 describe-instances --region $(REGION) --filters "Name=tag:Name,Values=$(NAME)" "Name=instance-state-name,Values=pending,running,stopping,stopped" --query "Reservations[0].Instances[0].InstanceId" --output text
IP_CMD = aws ec2 describe-instances --region $(REGION) --instance-ids $$ID --query "Reservations[0].Instances[0].PublicIpAddress" --output text

.PHONY: up down status tunnel install

# Allume l'instance, attend SSH, ouvre le tunnel vers l'API k3s.
up:
> ID=$$($(ID_CMD)); \
> aws ec2 start-instances --region $(REGION) --instance-ids $$ID > /dev/null; \
> aws ec2 wait instance-running --region $(REGION) --instance-ids $$ID; \
> IP=$$($(IP_CMD)); echo "Instance $$ID demarree, IP $$IP"; \
> until ssh -i $(KEY) -o StrictHostKeyChecking=accept-new -o ConnectTimeout=5 admin@$$IP true 2>/dev/null; do echo "attente SSH..."; sleep 5; done
> $(MAKE) --no-print-directory tunnel
> @echo "Pret. Attends 1 a 3 minutes que les pods remontent, puis : export KUBECONFIG=$(CURDIR)/ansible/kubeconfig-orion.yaml"

# Ouvre le tunnel local 6443 -> API k3s de l'instance.
tunnel:
> ID=$$($(ID_CMD)); IP=$$($(IP_CMD)); \
> ssh -S $(SOCK) -O exit admin@$$IP 2>/dev/null || true; rm -f $(SOCK); \
> ssh -i $(KEY) -M -S $(SOCK) -f -N -o ExitOnForwardFailure=yes -o StrictHostKeyChecking=accept-new -L 6443:127.0.0.1:6443 admin@$$IP; \
> echo "Tunnel ouvert : 127.0.0.1:6443 -> $$IP"

# Ferme le tunnel et ETEINT l'instance (le disque reste, plus de cout de calcul).
down:
> -ssh -S $(SOCK) -O exit admin@x 2>/dev/null; rm -f $(SOCK)
> ID=$$($(ID_CMD)); \
> aws ec2 stop-instances --region $(REGION) --instance-ids $$ID > /dev/null; \
> aws ec2 wait instance-stopped --region $(REGION) --instance-ids $$ID; \
> echo "Instance $$ID eteinte."

status:
> aws ec2 describe-instances --region $(REGION) --filters "Name=tag:Name,Values=$(NAME)" "Name=instance-state-name,Values=pending,running,stopping,stopped" --query "Reservations[].Instances[].[InstanceId,State.Name,PublicIpAddress]" --output text

# Installe k3s sur l'instance (a faire une seule fois) et recupere le kubeconfig.
install:
> ID=$$($(ID_CMD)); IP=$$($(IP_CMD)); \
> ansible-playbook -i "$$IP," -u admin --private-key $(KEY) -e ansible_python_interpreter=/usr/bin/python3 ansible/bootstrap-k3s.yml
> chmod 600 ansible/kubeconfig-orion.yaml

.PHONY: deploy

# Deploie Orion (Helm) sur k3s : derniere version publiee, ou VERSION=x.y.z.
# Prerequis : instance allumee (make up) et KUBECONFIG exporte.
deploy:
> V="$(VERSION)"; \
> [ -n "$$V" ] || V=$$(git ls-remote --tags --refs --sort=-v:refname origin 'v*' | head -n1 | sed 's#.*refs/tags/v##'); \
> [ -n "$$V" ] || { echo "Aucune version trouvee"; exit 1; }; \
> echo "Deploiement d'Orion en version $$V"; \
> KUBECONFIG=$(CURDIR)/ansible/kubeconfig-orion.yaml helm upgrade --install orion helm/orion \
>   --namespace orion --create-namespace \
>   --set backend.tag=$$V --set frontend.tag=$$V \
>   --atomic --timeout 5m
