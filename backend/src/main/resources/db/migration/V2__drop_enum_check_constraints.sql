-- O Hibernate (ddl-auto=update) criou restrições CHECK com a lista de valores de cada enum
-- nas colunas novas da Fase 1. Ele nunca as atualiza: uma categoria nova no código passaria a
-- ser rejeitada pelo banco. A validação dos valores fica no código (enums + Bean Validation).
-- Removemos todas as CHECK da tabela pelo catálogo, sem depender dos nomes gerados.
-- contype = 'c' pega só CHECK (não mexe em chave primária nem em NOT NULL).

DO $$
DECLARE
    constraint_name TEXT;
BEGIN
    FOR constraint_name IN
        SELECT conname FROM pg_constraint
        WHERE conrelid = 'tasks'::regclass AND contype = 'c'
    LOOP
        EXECUTE format('ALTER TABLE tasks DROP CONSTRAINT %I', constraint_name);
    END LOOP;
END $$;
