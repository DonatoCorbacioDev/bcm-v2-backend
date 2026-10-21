-- Stripe customer (cus_...) behind an organization's subscription, captured
-- from checkout.session.completed. Needed to open the Stripe Customer Portal
-- (cancel, change card, invoices) for that org, and reused on a later
-- checkout so a resubscribing org doesn't get a duplicate Stripe customer.
-- NULL for orgs that never paid through Stripe, including ones moved to a
-- paid tier by hand via the internal tier endpoint.
ALTER TABLE organizations
    ADD COLUMN stripe_customer_id VARCHAR(255);
