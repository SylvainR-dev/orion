# Instance EC2 dediee a Orion (k3s). Elle est faite pour etre eteinte
# entre deux sessions de travail : pas d'IP Elastic, disque reduit.

data "aws_vpc" "default" {
  default = true
}

data "aws_subnets" "default" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.default.id]
  }
}

data "aws_ami" "debian" {
  most_recent = true
  owners      = ["136693071363"] # Debian officiel

  filter {
    name   = "name"
    values = ["debian-12-amd64-*"]
  }
}

# Seul le SSH est ouvert, uniquement depuis l'IP admin.
# L'API k3s (6443) est atteinte par tunnel SSH, jamais exposee.
resource "aws_security_group" "orion_k3s" {
  name        = "orion-k3s-sg"
  description = "SSH depuis IP admin pour instance Orion k3s"
  vpc_id      = data.aws_vpc.default.id

  ingress {
    description = "SSH"
    from_port   = 22
    to_port     = 22
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
    Name    = "orion-k3s-sg"
    Project = "orion"
  }
}

resource "aws_instance" "orion_k3s" {
  ami                    = data.aws_ami.debian.id
  instance_type          = var.instance_type
  subnet_id              = element(data.aws_subnets.default.ids, 0)
  vpc_security_group_ids = [aws_security_group.orion_k3s.id]
  key_name               = var.key_name
  iam_instance_profile   = aws_iam_instance_profile.orion_k3s.name

  root_block_device {
    volume_size = 15 # reste facture meme instance arretee
    volume_type = "gp3"
  }

  tags = {
    Name    = "orion-k3s"
    Project = "orion"
  }
}
