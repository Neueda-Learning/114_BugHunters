
CREATE TABLE payment (
	id BIGINT auto_increment primary key,
	currency  varchar(20) not null,
    amount double not null,
    account_from varchar(255) not null,
    account_to varchar(255) not null,
    status ENUM('CREATED','VALIDATED','SENT','COMPLETED','FAILED') not null,
    idempotency_key varchar(255) not null unique,
    created_at datetime not null,
    updated_at datetime,
    type varchar(100) not null
    );