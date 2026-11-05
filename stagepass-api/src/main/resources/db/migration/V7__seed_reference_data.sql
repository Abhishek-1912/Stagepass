-- Reference data required in every environment.

insert into roles (name) values ('ATTENDEE'), ('ORGANIZER'), ('ADMIN');

insert into event_categories (name, slug, icon, sort_order) values
    ('Music',            'music',            'music',          1),
    ('Concerts',         'concerts',         'mic-vocal',      2),
    ('Technology',       'technology',       'cpu',            3),
    ('Business',         'business',         'briefcase',      4),
    ('Workshops',        'workshops',        'hammer',         5),
    ('Sports',           'sports',           'trophy',         6),
    ('Comedy',           'comedy',           'laugh',          7),
    ('Festivals',        'festivals',        'party-popper',   8),
    ('Food & Lifestyle', 'food-lifestyle',   'utensils',       9);

insert into platform_settings (key, value) values
    ('fees', '{"convenienceFeeBps":250,"convenienceFeeFlatPaise":1000,"gstBps":1800,"commissionBps":0,"refundFeeOnAttendeeRequest":false}'::jsonb);
