output "instance_id" {
  description = "ID de l'instance Orion k3s"
  value       = aws_instance.orion_k3s.id
}
