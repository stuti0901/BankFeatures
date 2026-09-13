package com.example.accounts.dto;

//import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import lombok.Data;

@Data
@Schema(
        name = "Accounts",
        description = "Schema to hold Account information"
)
public class AccountsDto {

    @NotNull(message = "AccountNumber can not be null")
    @Min(value = 1000000000L, message = "AccountNumber must be 10 digits")
    @Max(value = 9999999999L, message = "AccountNumber must be 10 digits")
    @Schema(
            description = "Account Number of Eazy Bank account", example = "3454433243"
    )
    private Long accountNumber;

    @NotEmpty(message = "AccountType can not be a null or empty")
    @Schema(
            description = "Account type of Eazy Bank account", example = "Savings"
    )
    private String accountType;
//
//    @NotEmpty(message = "BranchAddress can not be a null or empty")
//    @Schema(
//            description = "Eazy Bank branch address", example = "123 NewYork"
//    )
    private String branchAddress;
}