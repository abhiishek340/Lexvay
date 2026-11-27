# Network ACL on the private subnets (backend tasks and database). The SSRF guard in the application resolves a host
# and checks the address, but the HTTP client resolves it again, so a DNS-rebinding answer could still point a fetch
# at an internal address. This ACL closes that window at the network: nothing in a private subnet may open a
# connection to a private range outside the VPC.
#
# Rules are evaluated in ascending number and the first match wins. Network ACLs are stateless, so return traffic is
# allowed explicitly.
#
# Link-local addresses (169.254.0.0/16: the instance metadata service, the VPC DNS resolver, the ECS credentials
# endpoint the task needs at 169.254.170.2) never pass through a network ACL, so no rule here can block or allow them.
# The application's SsrfGuard remains the control for those.

locals {
  # Ranges that are never a legitimate target outside the VPC: RFC 1918 and the carrier-grade NAT range.
  private_ranges = ["10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "100.64.0.0/10"]

  nacl_egress_rules = merge(
    { "100" = { action = "allow", cidr = var.vpc_cidr } },
    { for i, cidr in local.private_ranges : tostring(110 + 10 * i) => { action = "deny", cidr = cidr } },
    { "200" = { action = "allow", cidr = "0.0.0.0/0" } },
  )
}

resource "aws_network_acl" "private" {
  vpc_id     = aws_vpc.main.id
  subnet_ids = aws_subnet.private[*].id

  tags = { Name = "${local.name}-private" }
}

resource "aws_network_acl_rule" "egress" {
  for_each = local.nacl_egress_rules

  network_acl_id = aws_network_acl.private.id
  egress         = true
  rule_number    = tonumber(each.key)
  rule_action    = each.value.action
  protocol       = "-1"
  cidr_block     = each.value.cidr
}

resource "aws_network_acl_rule" "ingress_from_vpc" {
  network_acl_id = aws_network_acl.private.id
  egress         = false
  rule_number    = 100
  rule_action    = "allow"
  protocol       = "-1"
  cidr_block     = var.vpc_cidr
}

# Replies to connections the tasks opened to the internet (model API, image registry) come back to ephemeral ports.
resource "aws_network_acl_rule" "ingress_return_traffic" {
  network_acl_id = aws_network_acl.private.id
  egress         = false
  rule_number    = 200
  rule_action    = "allow"
  protocol       = "tcp"
  from_port      = 1024
  to_port        = 65535
  cidr_block     = "0.0.0.0/0"
}
