SELECT 
	created,
	location_id,
	price
FROM solarnode.sn_price_datum
WHERE uploaded IS NULL
ORDER BY created, location_id
