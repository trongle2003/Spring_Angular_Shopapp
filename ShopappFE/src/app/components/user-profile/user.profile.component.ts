import { Component, ViewChild, OnInit } from '@angular/core';
import {
  FormBuilder,
  FormGroup,
  Validators,
  ValidationErrors,
  ValidatorFn,
  AbstractControl
} from '@angular/forms';

import { Router, ActivatedRoute } from '@angular/router';
import { UserService } from '../../services/user.service';
import { TokenService } from '../../services/token.service';
import { UserResponse } from '../../responses/user/user.response';
import { UpdateUserDTO } from '../../dtos/user/update.user.dto';

import { HeaderComponent } from '../header/header.component';
import { FooterComponent } from '../footer/footer.component';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';


@Component({
  selector: 'user-profile',
  templateUrl: './user.profile.component.html',
  styleUrls: ['./user.profile.component.scss'],
  standalone: true,
  imports: [
    FooterComponent,
    HeaderComponent,
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
  ],
})
export class UserProfileComponent implements OnInit {
  userResponse?: UserResponse;
  userProfileForm: FormGroup;
  token: string | null = '';

  constructor(
    private formBuilder: FormBuilder,
    private activatedRoute: ActivatedRoute,
    private userService: UserService,
    private router: Router,
    private tokenService: TokenService,
  ) {
    this.userProfileForm = this.formBuilder.group({
      fullname: [''],
      address: ['', [Validators.minLength(3)]],
      password: ['', [Validators.minLength(3)]],
      retype_password: ['', [Validators.minLength(3)]],
      date_of_birth: [Date.now()],
    }, {
      validators: this.passwordMatchValidator// Custom validator function for password match
    });
  }
  ngOnInit(): void {
    throw new Error('Method not implemented.');
  }

  nngOnInit(): void {
    this.token = this.tokenService.getToken();

    if (!this.token) {
      alert('Không tìm thấy token, vui lòng đăng nhập lại.');
      this.router.navigate(['/login']);
      return;
    }

    this.userService.getUserDetail(this.token).subscribe({
      next: (response: any) => {
        const user: UserResponse = {
          ...response,
          date_of_birth: new Date(response.date_of_birth),
        };
        this.userResponse = user;

        this.userProfileForm.patchValue({
          fullname: user.fullname,
          address: user.address,
          date_of_birth: user.date_of_birth.toISOString().substring(0, 10),
        });

        this.userService.saveUserResponseToLocalStorage(user);
      },
      error: (error: any) => {
        alert(error.error.message);
      }
    });
  }


  passwordMatchValidator(): ValidatorFn {
    return (formGroup: AbstractControl): ValidationErrors | null => {
      const password = formGroup.get('password')?.value;
      const retypedPassword = formGroup.get('retype_password')?.value;
      if (password !== retypedPassword) {
        return { passwordMismatch: true };
      }

      return null;
    };
  }
  save(): void {
    if (!this.token) {
      alert('Không tìm thấy token. Vui lòng đăng nhập lại.');
      this.router.navigate(['/login']);
      return;
    }

    if (this.userProfileForm.valid) {
      const updateUserDTO: UpdateUserDTO = {
        fullname: this.userProfileForm.get('fullname')?.value,
        address: this.userProfileForm.get('address')?.value,
        password: this.userProfileForm.get('password')?.value,
        retype_password: this.userProfileForm.get('retype_password')?.value,
        date_of_birth: this.userProfileForm.get('date_of_birth')?.value
      };

      this.userService.updateUserDetail(this.token, updateUserDTO)
        .subscribe({
          next: (response: any) => {
            this.userService.removeUserFromLocalStorage();
            this.tokenService.removeToken();
            this.router.navigate(['/login']);
          },
          error: (error: any) => {
            alert(error.error.message);
          }
        });
    } else {
      if (this.userProfileForm.hasError('passwordMismatch')) {
        alert('Mật khẩu và mật khẩu gõ lại chưa chính xác');
      }
    }
  }

}

