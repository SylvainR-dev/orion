import { Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { Organization } from './organization.service';
import { HttpClient } from '@angular/common/http';
import { API_BASE_URL } from './config'

@Injectable({ providedIn: 'root' })
export class PersonService {
  constructor(private client: HttpClient) {
  }

  async fetchById(id: number): Promise<Person> {
    const response = this.client.get(`${API_BASE_URL}/api/persons/${id}`)
    const person = await firstValueFrom(response) as Person
    person.organizations = await this.fetchPersonOrganizations(id)
    return person
  }

  async fetchAll(): Promise<Person[]> {
    const response = this.client.get(`${API_BASE_URL}/api/persons`)
    const persons = await firstValueFrom(response) as Person[]
    return persons
  }

  async fetchPersonOrganizations(personId: number): Promise<Organization[]> {
    const response = this.client.get(`${API_BASE_URL}/api/organizations`)
    const allOrganizations = await firstValueFrom(response) as Organization[]
    return allOrganizations
      .filter(org => org.personIds.includes(personId))
      .map(org => ({ ...org, persons: [] as Person[] }))
  }

  async deleteById(id: number): Promise<void> {
    const response = this.client.delete(`${API_BASE_URL}/api/persons/${id}`)
    await firstValueFrom(response)
    return
  }

  async save(person: Person): Promise<Person> {
    let response;
    if (person.id === undefined) {
      response = this.client.post(`${API_BASE_URL}/api/persons`, {
        firstName: person.firstName,
        lastName: person.lastName,
        bio: person.bio,
        phone: person.phone,
        email: person.email,
      })
    } else {
      response = this.client.put(`${API_BASE_URL}/api/persons/${person.id}`, {
        firstName: person.firstName,
        lastName: person.lastName,
        bio: person.bio,
        phone: person.phone,
        email: person.email,
      })
    }

    person = await firstValueFrom(response) as Person
    person.organizations = await this.fetchPersonOrganizations(person.id as number)

    return person
  }

}


export interface Person {
  id?: number
  firstName: string
  lastName: string
  email: string
  phone: string
  bio: string
  createdAt: Date
  updatedAt?: Date
  organizations: Organization[];
}