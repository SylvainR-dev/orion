output "instance_id" {
  description = "ID de l'instance EC2 Orion"
  value       = aws_instance.orion.id
}

output "instance_public_ip" {
  description = "IP publique de l'instance EC2 Orion (à utiliser dans l'inventaire Ansible et le kubeconfig k3s)"
  value       = aws_instance.orion.public_ip
}