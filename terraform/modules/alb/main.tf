# ALB Module - Application Load Balancer with Target Groups and Routing

# Application Load Balancer
resource "aws_lb" "main" {
  name               = "${var.project_name}-alb"
  internal           = false
  load_balancer_type = "application"
  security_groups    = [var.alb_security_group_id]
  subnets            = var.public_subnet_ids

  enable_deletion_protection = false

  tags = merge(
    var.tags,
    {
      Name = "${var.project_name}-alb"
    }
  )
}

# Target Groups
resource "aws_lb_target_group" "services" {
  for_each = { for svc in var.services : svc.name => svc }

  name        = "${var.project_name}-${each.value.name}-tg"
  port        = each.value.port
  protocol    = "HTTP"
  vpc_id      = var.vpc_id
  target_type = "ip"

  health_check {
    enabled             = true
    healthy_threshold   = 2
    interval            = 30
    matcher             = "200"
    path                = "/actuator/health"
    port                = "traffic-port"
    protocol            = "HTTP"
    timeout             = 5
    unhealthy_threshold = 3
  }

  tags = merge(
    var.tags,
    {
      Name = "${var.project_name}-${each.value.name}-tg"
    }
  )
}

# HTTP Listener (Port 80)
resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.main.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.services["api-gateway"].arn
  }
}

# Listener Rules for Path-based Routing
resource "aws_lb_listener_rule" "user_service" {
  listener_arn = aws_lb_listener.http.arn
  priority     = 100

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.services["user-service"].arn
  }

  condition {
    path_pattern {
      values = ["/api/users/*", "/api/auth/*", "/api/canvas/*", "/internal/v1/credentials/*"]
    }
  }
}

resource "aws_lb_listener_rule" "course_service" {
  listener_arn = aws_lb_listener.http.arn
  priority     = 200

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.services["course-service"].arn
  }

  condition {
    path_pattern {
      values = ["/api/courses/*", "/api/assignments/*"]
    }
  }
}

resource "aws_lb_listener_rule" "schedule_service" {
  listener_arn = aws_lb_listener.http.arn
  priority     = 300

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.services["schedule-service"].arn
  }

  condition {
    path_pattern {
      values = ["/api/schedules/*", "/api/tasks/*"]
    }
  }
}
