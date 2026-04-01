# Secrets Management

This application uses environment variables to manage sensitive information. **DO NOT** commit actual values to the repository.

## Required Environment Variables

### Database
- `DB_URL`: JDBC URL for the database (e.g., `jdbc:mysql://localhost:3306/cryptello`).
- `DB_USERNAME`: Database username.
- `DB_PASSWORD`: Database password.

### Authentication
- `JWT_SECRET`: Secret key used for signing JWT tokens.

### Payments
- `STRIPE_API_KEY`: Secret key for Stripe API.
- `STRIPE_WEBHOOK_SECRET`: Secret for verifying Stripe webhooks.
- `RAZORPAY_API_KEY`: Public key for Razorpay.
- `RAZORPAY_API_SECRET`: Secret key for Razorpay.

## Local Development
For local development, you can set these variables in your IDE's run configuration or use a `.env` file if you have a loader configured (not default in Spring Boot).
