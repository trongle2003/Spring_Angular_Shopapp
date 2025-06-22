import { IsNumber, IsString } from "class-validator";


export class UpdateProductDTO {
    @IsString()
    name: string;
    @IsNumber()
    price: number;

    @IsNumber()
    stock_quantity: number;

    @IsString()
    description: string;
    @IsNumber()
    category_id: number;


    constructor(data: any) {
        this.name = data.name;
        this.price = data.price;
        this.description = data.description;
        this.category_id = data.category_id;
        this.stock_quantity = data.stock_quantity;
    }
}