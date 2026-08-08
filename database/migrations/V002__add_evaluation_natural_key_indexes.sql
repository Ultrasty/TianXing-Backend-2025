-- Verified against mysql_backup_20251023.sql: zero duplicate groups for all four keys.
-- Re-run the SELECT checks against the target database before applying this migration.

SELECT year, COUNT(*) AS duplicate_count
FROM obs_enso
GROUP BY year
HAVING COUNT(*) > 1;

SELECT year, month, var_model, COUNT(*) AS duplicate_count
FROM tj_nao
GROUP BY year, month, var_model
HAVING COUNT(*) > 1;

SELECT year, month, day, var_model, COUNT(*) AS duplicate_count
FROM tj_sic
GROUP BY year, month, day, var_model
HAVING COUNT(*) > 1;

SELECT year, month, var_model, COUNT(*) AS duplicate_count
FROM tj_sie
GROUP BY year, month, var_model
HAVING COUNT(*) > 1;

ALTER TABLE obs_enso ADD UNIQUE KEY uk_obs_enso_year (year);
ALTER TABLE tj_nao ADD UNIQUE KEY uk_tj_nao_year_month_model (year, month, var_model);
ALTER TABLE tj_sic ADD UNIQUE KEY uk_tj_sic_date_model (year, month, day, var_model);
ALTER TABLE tj_sie ADD UNIQUE KEY uk_tj_sie_year_month_model (year, month, var_model);
