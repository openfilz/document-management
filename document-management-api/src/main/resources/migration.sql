do
$$
begin
    IF EXISTS (select 1 from information_schema.columns where table_name='audit_logs' and column_name='resource_id' and data_type <> 'uuid')
        THEN
    alter table audit_logs add column resource_id_2 UUID;
    update audit_logs set resource_id_2 = resource_id::uuid;
    alter table audit_logs drop column resource_id;
    alter table audit_logs rename column resource_id_2 to resource_id;
    alter table audit_logs alter column resource_id TYPE UUID;
    END IF;
end;
$$