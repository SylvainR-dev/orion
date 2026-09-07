# ---------------------------------------------------------------------------
# Orion - Infrastructure "home lab" : une seule instance EC2 exécutant k3s
# Remplace les anciens modules terraform-aws-modules/vpc + eks
# ---------------------------------------------------------------------------

# On réutilise le VPC par défaut du compte AWS plutôt que d'en provisionner
# un dédié : plus de NAT Gateway, plus de coût réseau supplémentaire.
data "aws_vpc" "default" {
  default = true
}

data "aws_subnets" "default" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.default.id]
  }
}

# AMI Debian 12 (cohérent avec le reste du projet, l'équipe Ops maîtrise Debian)
data "aws_ami" "debian" {
  most_recent = true
  owners      = ["136693071363"] # Debian officiel

  filter {
    name   = "name"
    values = ["debian-12-amd64-*"]
  }
}

resource "aws_security_group" "orion_ec2" {
  name        = "orion-ec2-sg"
  description = "Acces SSH + API k3s + HTTP applicatif pour instance Orion"
  vpc_id      = data.aws_vpc.default.id

  ingress {
    description = "SSH"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = [var.admin_ip_cidr]
  }

  ingress {
    description = "API k3s (kubectl distant)"
    from_port   = 6443
    to_port     = 6443
    protocol    = "tcp"
    cidr_blocks = [var.admin_ip_cidr]
  }

  ingress {
    description = "Acces applicatif front-back NodePort ou port-forward de test"
    from_port   = 8080
    to_port     = 8090
    protocol    = "tcp"
    cidr_blocks = [var.admin_ip_cidr]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name    = "orion-ec2-sg"
    Project = "orion"
  }
}

resource "aws_key_pair" "orion" {
  key_name   = "orion-ec2-key"
  public_key = file(var.ssh_public_key_path)
}

resource "aws_instance" "orion" {
  ami                    = data.aws_ami.debian.id
  instance_type          = var.instance_type
  subnet_id              = element(data.aws_subnets.default.ids, 0)
  vpc_security_group_ids = [aws_security_group.orion_ec2.id]
  key_name               = aws_key_pair.orion.key_name

  root_block_device {
    volume_size = 30 # k3s + images Docker + PLG stack : on garde de la marge
    volume_type = "gp3"
  }

  tags = {
    Name    = "orion-ec2"
    Project = "orion"
  }
}