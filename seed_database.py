import psycopg2

def main():
    db_uri = "postgres://neondb_owner:npg_jLHFwzIR4q0W@ep-aged-truth-aow79yjg.c-2.ap-southeast-1.aws.neon.tech/neondb?sslmode=require"
    conn = psycopg2.connect(db_uri)
    cur = conn.cursor()
    
    print("Truncating database tables with identity restart...")
    # Truncate tables in cascade to handle all foreign key constraints safely
    cur.execute("""
        TRUNCATE TABLE 
            categories, 
            products, 
            orders, 
            order_items, 
            reviews, 
            review_helpful, 
            review_images, 
            payment_transactions 
        RESTART IDENTITY CASCADE;
    """)
    conn.commit()
    print("Truncation successful.")

    # 1. Categories List
    categories = [
        "Bathroom Cleaner",
        "Hand Wash",
        "Floor Cleaner",
        "Glass Cleaner",
        "Germs Killer",
        "Detergent Powder",
        "Detergent Liquid",
        "Dish Wash",
        "Car Shampoo",
        "Anti Stain Liquid",
        "Room Freshener",
        "Pet Shampoo"
    ]
    
    category_id_map = {}
    print("\nInserting categories...")
    for idx, cat_name in enumerate(categories, start=1):
        cur.execute("INSERT INTO categories (name) VALUES (%s) RETURNING id;", (cat_name,))
        cat_id = cur.fetchone()[0]
        category_id_map[cat_name] = cat_id
        print(f"Inserted Category: '{cat_name}' with ID={cat_id}")
    conn.commit()

    # 2. Products List mapping
    products_data = [
        # Bathroom Cleaner
        ("Toilet Cleaner 1L", "Premium quality toilet cleaner for clean, shining surface and germ protection.", 80.0, 100, "https://images.unsplash.com/photo-1563453392212-326f5e854473", "Bathroom Cleaner"),
        ("Toilet Cleaner 500ml", "Standard quality toilet cleaner for clean, shining surface and germ protection.", 45.0, 100, "https://images.unsplash.com/photo-1563453392212-326f5e854473", "Bathroom Cleaner"),
        ("Tiles Cleaner 1L", "Effective tile and floor cleaner designed to remove hard water stains and dirt.", 110.0, 100, "https://images.unsplash.com/photo-1563453392212-326f5e854473", "Bathroom Cleaner"),
        ("Tiles Cleaner 500ml", "Effective tile and floor cleaner designed to remove hard water stains and dirt.", 65.0, 100, "https://images.unsplash.com/photo-1563453392212-326f5e854473", "Bathroom Cleaner"),
        
        # Hand Wash
        ("Hand Wash 1L", "Gentle foaming hand wash with moisturizer to keep hands soft and germ-free.", 125.0, 100, "https://images.unsplash.com/photo-1584305574647-acf8069a7fc7", "Hand Wash"),
        ("Hand Wash 500ml", "Gentle foaming hand wash with moisturizer to keep hands soft and germ-free.", 65.0, 100, "https://images.unsplash.com/photo-1584305574647-acf8069a7fc7", "Hand Wash"),
        ("Hand Wash 1L Fragrance free", "Dermatologist-tested fragrance-free hand wash for sensitive skin types.", 125.0, 100, "https://images.unsplash.com/photo-1584305574647-acf8069a7fc7", "Hand Wash"),
        ("Hand Wash 500ml fragrance free", "Dermatologist-tested fragrance-free hand wash for sensitive skin types.", 65.0, 100, "https://images.unsplash.com/photo-1584305574647-acf8069a7fc7", "Hand Wash"),
        
        # Floor Cleaner
        ("White Phenyl 1L", "Classic White Phenyl with natural pine fragrance for clean and fresh floors.", 30.0, 100, "https://images.unsplash.com/photo-1604335399105-a0c585fd81a1", "Floor Cleaner"),
        ("White Phenyl 5L", "Classic White Phenyl with natural pine fragrance for clean and fresh floors.", 125.0, 100, "https://images.unsplash.com/photo-1604335399105-a0c585fd81a1", "Floor Cleaner"),
        ("Phenyl Compound 1L -> 25L", "Highly concentrated Phenyl Compound. 1 Litre dilutes up to 25 Litres of active white floor phenyl.", 300.0, 100, "https://images.unsplash.com/photo-1585421514738-01798e348b17", "Floor Cleaner"),
        ("Phenyl Compound 500ml -> 12.5L", "Highly concentrated Phenyl Compound. 500ml dilutes up to 12.5 Litres of active floor phenyl.", 160.0, 100, "https://images.unsplash.com/photo-1585421514738-01798e348b17", "Floor Cleaner"),
        ("Pink Phenyl 1L", "Perfumed Pink Phenyl with long-lasting floral fragrance for deep cleaning.", 70.0, 100, "https://images.unsplash.com/photo-1604335399105-a0c585fd81a1", "Floor Cleaner"),
        ("Pink Phenyl 5L", "Perfumed Pink Phenyl with long-lasting floral fragrance for deep cleaning.", 300.0, 100, "https://images.unsplash.com/photo-1604335399105-a0c585fd81a1", "Floor Cleaner"),
        ("Pink Phenyl Compound 1L -> 30L", "Concentrated Pink Phenyl. 1 Litre makes up to 30 Litres of floral pink floor cleaner.", 350.0, 100, "https://images.unsplash.com/photo-1585421514738-01798e348b17", "Floor Cleaner"),
        ("Pink Phenyl Compound 500ml -> 15L", "Concentrated Pink Phenyl. 500ml makes up to 15 Litres of floral pink floor cleaner.", 180.0, 100, "https://images.unsplash.com/photo-1585421514738-01798e348b17", "Floor Cleaner"),
        ("Black Phenyl 1L", "Strong black disinfectant fluid for deep cleaning of washrooms and outdoors.", 70.0, 100, "https://images.unsplash.com/photo-1590794056226-79ef3a8147e1", "Floor Cleaner"),
        ("Black Phenyl 5L", "Strong black disinfectant fluid for deep cleaning of washrooms and outdoors.", 300.0, 100, "https://images.unsplash.com/photo-1590794056226-79ef3a8147e1", "Floor Cleaner"),
        
        # Glass Cleaner
        ("Glass Cleaner 1L", "Streak-free formula for windows, mirrors, car windshields and display cases.", 60.0, 100, "https://images.unsplash.com/photo-1520607162513-77705c0f0d4a", "Glass Cleaner"),
        ("Glass Cleaner 1L with spray", "Streak-free formula inside a handy premium trigger spray container.", 80.0, 100, "https://images.unsplash.com/photo-1520607162513-77705c0f0d4a", "Glass Cleaner"),
        ("Glass Cleaner 5L", "Bulk pack glass cleaner fluid for commercial refilling and heavy usage.", 250.0, 100, "https://images.unsplash.com/photo-1603712725038-e9334ae8f39f", "Glass Cleaner"),
        
        # Germs Killer
        ("Germdral 1L", "Advanced antiseptic disinfectant liquid for active hygiene and germ protection.", 70.0, 100, "https://images.unsplash.com/photo-1584483766114-2cea6facdf57", "Germs Killer"),
        ("Germdral 1L with spray", "Antiseptic disinfectant liquid with handy spray pump for quick sanitization.", 85.0, 100, "https://images.unsplash.com/photo-1584483766114-2cea6facdf57", "Germs Killer"),
        ("Germdral 5L", "Bulk sanitizer and germ killer liquid for refillable dispensers.", 300.0, 100, "https://images.unsplash.com/photo-1584483766114-2cea6facdf57", "Germs Killer"),
        
        # Detergent Powder
        ("Detergent Powder 1kg (Super Pack)", "Premium washing powder for quick dirt removal and bright clothes.", 70.0, 100, "https://images.unsplash.com/photo-1583947215259-38e31be8751f", "Detergent Powder"),
        ("Detergent Powder 500gm (Super Pack)", "Premium washing powder for quick dirt removal and bright clothes.", 40.0, 100, "https://images.unsplash.com/photo-1583947215259-38e31be8751f", "Detergent Powder"),
        ("Detergent Powder 1kg (Loose Pack)", "Budget laundry detergent powder for bulk washing and tough cleaning.", 60.0, 100, "https://images.unsplash.com/photo-1583947215259-38e31be8751f", "Detergent Powder"),
        
        # Detergent Liquid
        ("Safe Wash 1L", "Gentle liquid detergent protecting fabrics and keeping colors vibrant.", 125.0, 100, "https://images.unsplash.com/photo-1583947215259-38e31be8751f", "Detergent Liquid"),
        ("Safe Wash 500mL", "Gentle liquid detergent protecting fabrics and keeping colors vibrant.", 65.0, 100, "https://images.unsplash.com/photo-1583947215259-38e31be8751f", "Detergent Liquid"),
        
        # Dish Wash
        ("Dish Wash Gel 1L", "Tough grease removal gel with lemon extracts, gentle on hands and utensils.", 110.0, 100, "https://images.unsplash.com/photo-1603712725038-e9334ae8f39f", "Dish Wash"),
        ("Dish Wash Gel 500mL", "Tough grease removal gel with lemon extracts, gentle on hands and utensils.", 60.0, 100, "https://images.unsplash.com/photo-1603712725038-e9334ae8f39f", "Dish Wash"),
        ("Dish Wash Liquid 1L", "Liquid dishwashing soap for grease removal and clean plates.", 70.0, 100, "https://images.unsplash.com/photo-1603712725038-e9334ae8f39f", "Dish Wash"),
        ("Dish Wash Soap Compound 1L -> 10L", "Concentrated dish wash liquid. 1 Litre dilutes to 10 Litres of cleaning soap.", 340.0, 100, "https://images.unsplash.com/photo-1603712725038-e9334ae8f39f", "Dish Wash"),
        ("Dish Wash Soap Compound 500mL -> 5L", "Concentrated dish wash liquid. 500ml dilutes to 5 Litres of cleaning soap.", 170.0, 100, "https://images.unsplash.com/photo-1603712725038-e9334ae8f39f", "Dish Wash"),
        
        # Car Shampoo
        ("Car Shampoo 1L", "Rich foaming car wash shampoo for spot-free shine and paint protection.", 120.0, 100, "https://images.unsplash.com/photo-1527515637462-cff94eecc1ac", "Car Shampoo"),
        ("Car Shampoo 500ml", "Rich foaming car wash shampoo for spot-free shine and paint protection.", 65.0, 100, "https://images.unsplash.com/photo-1527515637462-cff94eecc1ac", "Car Shampoo"),
        
        # Anti Stain Liquid
        ("Anti Stain Liquid 1L", "Powerful chemical solution for removing grease, oil, rust and tough surface spots.", 150.0, 100, "https://images.unsplash.com/photo-1604335399105-a0c585fd81a1", "Anti Stain Liquid"),
        ("Anti Stain Liquid 500ml", "Powerful chemical solution for removing grease, oil, rust and tough surface spots.", 80.0, 100, "https://images.unsplash.com/photo-1604335399105-a0c585fd81a1", "Anti Stain Liquid"),
        
        # Room Freshener
        ("Room Freshener 1L", "Long-lasting fragrance spray to neutralize odors and refresh room atmosphere.", 200.0, 100, "https://images.unsplash.com/photo-1603006905003-be475563bc59", "Room Freshener"),
        ("Room Freshener 1L with spray", "Long-lasting room freshener with trigger spray for instant deodorizing.", 220.0, 100, "https://images.unsplash.com/photo-1603006905003-be475563bc59", "Room Freshener"),
        
        # Pet Shampoo
        ("Pet Shampoo 1L", "Gentle skin and coat pet shampoo for cleaning, conditioning, and odor control.", 125.0, 100, "https://images.unsplash.com/photo-1583511655857-d19b40a7a54e", "Pet Shampoo"),
        ("Pet Shampoo 500mL", "Gentle skin and coat pet shampoo for cleaning, conditioning, and odor control.", 65.0, 100, "https://images.unsplash.com/photo-1583511655857-d19b40a7a54e", "Pet Shampoo")
    ]

    print("\nInserting products...")
    for idx, (name, desc, price, stock, img, cat_name) in enumerate(products_data, start=1):
        cat_id = category_id_map[cat_name]
        cur.execute("""
            INSERT INTO products (name, description, price, cost_price, stock, image_url, category_id, active) 
            VALUES (%s, %s, %s, %s, %s, %s, %s, TRUE);
        """, (name, desc, price, price, stock, img, cat_id))
        print(f"Inserted Product: '{name}' in Category: '{cat_name}'")
        
    conn.commit()
    print("\nReseed successful! All categories and products have been updated.")
    
    cur.close()
    conn.close()

if __name__ == '__main__':
    main()
