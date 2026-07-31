
  create table payment_history (
	id bigint auto_increment primary  key,
    payment_id bigint not null,
    old_status ENUM('CREATED','VALIDATED','SENT','COMPLETED','FAILED') NOT NULL,
    new_status ENUM('CREATED','VALIDATED','SENT','COMPLETED','FAILED') NOT NULL,
    changed_at datetime not null,
    remarks varchar(500),
    type varchar(100),
    constraint fk_payment_history_payment foreign key (payment_id) references payment(id) on delete cascade
    );