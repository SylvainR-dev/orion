# --- CD depuis GitHub Actions : OIDC + SSM, sans aucune cle stockee dans GitHub ---

# Fournisseur d'identite OIDC de GitHub Actions
resource "aws_iam_openid_connect_provider" "github" {
  url            = "https://token.actions.githubusercontent.com"
  client_id_list = ["sts.amazonaws.com"]
}

# Role endosse par le job "deploy". La condition limite l'acces a ce depot
# et a l'environnement GitHub "production" (donc a l'approbation manuelle).
resource "aws_iam_role" "github_deploy" {
  name = "orion-github-deploy"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Federated = aws_iam_openid_connect_provider.github.arn }
      Action    = "sts:AssumeRoleWithWebIdentity"
      Condition = {
        StringEquals = {
          "token.actions.githubusercontent.com:aud" = "sts.amazonaws.com"
          "token.actions.githubusercontent.com:sub" = "repo:${var.github_repo}:environment:production"
        }
      }
    }]
  })
}

# Droits minimaux : envoyer une commande SSM a CETTE instance uniquement.
resource "aws_iam_role_policy" "github_deploy" {
  name = "orion-deploy-ssm"
  role = aws_iam_role.github_deploy.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = "ssm:SendCommand"
        Resource = [
          "arn:aws:ssm:${var.aws_region}::document/AWS-RunShellScript",
          aws_instance.orion_k3s.arn,
        ]
      },
      {
        Effect   = "Allow"
        Action   = ["ssm:GetCommandInvocation", "ssm:ListCommandInvocations", "ssm:DescribeInstanceInformation"]
        Resource = "*"
      },
      {
        Effect   = "Allow"
        Action   = "ec2:DescribeInstances"
        Resource = "*"
      },
    ]
  })
}

# Role de l'instance : lui permet de s'enregistrer aupres de SSM.
resource "aws_iam_role" "orion_instance" {
  name = "orion-k3s-instance"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ec2.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy_attachment" "orion_instance_ssm" {
  role       = aws_iam_role.orion_instance.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_instance_profile" "orion_k3s" {
  name = "orion-k3s-instance"
  role = aws_iam_role.orion_instance.name
}
